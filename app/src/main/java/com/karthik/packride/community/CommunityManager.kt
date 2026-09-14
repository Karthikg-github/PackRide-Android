package com.karthik.packride.community

import android.content.Context
import android.location.Location
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Aug 30, 2026 — Community visual+functionality parity pass. Replaces the
// previous model entirely rather than extending it: the old CommunityManager
// showed a public directory of EVERY community that exists with a one-tap
// "Join" (no passcode at all), plus an unrelated "Riders" tab that was
// actually just every app user with a profile, nothing to do with community
// membership. Real iOS communities are private — passcode-gated, found only
// by someone sharing the ID + passcode out of band — with no public browse
// list at all. Old Android model was a genuine, if unintentional, privacy
// gap versus iOS, not just a lighter feature set. See CommunityMembershipStore
// (create/join with passcode) and CommunityScreen.kt for the rest of this pass.

/** A private, passcode-gated community — 1:1 port of iOS's Community model. */
data class Community(
    val id: String,
    val name: String,
    val passcode: String,
    val createdBy: String,
    val memberCount: Int,
    val createdAt: Double = 0.0
)

/**
 * One community's member record — port of iOS's CommunityMember. authUID
 * bridges community membership (keyed by device ID, myID below) to the
 * follow system (keyed by Firebase account ID) — same bridge iOS uses for
 * MemberRow's Follow button.
 */
data class CommunityMember(
    val id: String,
    val name: String,
    val initials: String,
    val isRiding: Boolean,
    val latitude: Double,
    val longitude: Double,
    val speed: Double,
    val lastSeen: Double,
    val authUID: String = ""
)

/**
 * One open community's live detail state — port of iOS's (per-community)
 * CommunityManager. A new instance is created per community the user opens,
 * same as iOS's CommunityDetailView creating a fresh CommunityManager per
 * community. Note on "Riding Now": updateRidingStatus() below is real,
 * functioning Firebase-write plumbing (matches iOS's), but — same as iOS's
 * own CommunityView.swift, where nothing outside that file ever calls
 * updateRidingStatus either — nothing in this ride-recording pipeline calls
 * it yet on either platform, so activeRiders will typically be empty in
 * practice. Wiring it into real ride sessions (SoloRideSession etc.) is a
 * genuinely separate feature neither app has actually shipped, not an
 * Android-specific gap.
 */
class CommunityManager(context: Context, val communityID: String) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference
    private var membersListener: ValueEventListener? = null

    val myID: String
        get() = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: ""

    val isCreator: Boolean
        get() {
            val creator = _myCommunity.value?.createdBy.orEmpty()
            val authUID = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            return creator.isNotEmpty() && (creator == myID || creator == authUID)
        }

    private val _myCommunity = MutableStateFlow<Community?>(null)
    val myCommunity: StateFlow<Community?> = _myCommunity.asStateFlow()

    private val _members = MutableStateFlow<List<CommunityMember>>(emptyList())
    val members: StateFlow<List<CommunityMember>> = _members.asStateFlow()

    private val _activeRiders = MutableStateFlow<List<CommunityMember>>(emptyList())
    val activeRiders: StateFlow<List<CommunityMember>> = _activeRiders.asStateFlow()

    /** Flips true the moment this community's Firebase node is observed gone (creator deleted it). */
    private val _wasDeleted = MutableStateFlow(false)
    val wasDeleted: StateFlow<Boolean> = _wasDeleted.asStateFlow()

    fun start(cached: Community?) {
        _myCommunity.value = cached
        loadCommunity()
        listenForMembers()
        stampMyAuthUID()
    }

    fun stop() {
        stopListeningForMembers()
    }

    // Stamps this device's Firebase-account UID onto its own member record
    // every time the community screen opens, not just when a ride starts —
    // self-heals a member row whose authUID predates this feature (or who
    // just hasn't ridden yet) without requiring a ride, matching iOS.
    private fun stampMyAuthUID() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isEmpty()) return
        val members = db.child("communities").child(communityID).child("members")
        val currentRef = members.child(myID)
        currentRef.get().addOnSuccessListener { current ->
            if (current.exists()) {
                currentRef.child("authUID").setValue(uid)
                return@addOnSuccessListener
            }
            // Builds before the device-ID parity correction keyed Android
            // memberships by auth UID. Move that one legacy record to the
            // same installation-ID shape iOS uses, preserving live fields.
            members.child(uid).get().addOnSuccessListener { legacy ->
                val existing = legacy.value as? Map<*, *>
                if (existing == null) {
                    currentRef.child("authUID").setValue(uid)
                    return@addOnSuccessListener
                }
                val migrated = existing.entries.associate { it.key.toString() to it.value }.toMutableMap()
                migrated["id"] = myID
                migrated["authUID"] = uid
                db.updateChildren(mapOf(
                    "communities/$communityID/members/$myID" to migrated,
                    "communities/$communityID/members/$uid" to null
                ))
            }
        }
    }

    private fun loadCommunity() {
        db.child("communities").child(communityID).get().addOnSuccessListener { snap ->
            val data = snap.value as? Map<*, *> ?: return@addOnSuccessListener
            _myCommunity.value = Community(
                id = communityID,
                name = data["name"] as? String ?: _myCommunity.value?.name ?: "Community",
                passcode = data["passcode"] as? String ?: _myCommunity.value?.passcode ?: "",
                createdBy = data["createdBy"] as? String ?: "",
                memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
                createdAt = (data["createdAt"] as? Number)?.toDouble() ?: 0.0
            )
        }
    }

    private fun listenForMembers() {
        stopListeningForMembers()
        val ref = db.child("communities").child(communityID).child("members")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // The whole community node (name/passcode/members) is removed
                // in one shot by deleteCommunity() — this members listener
                // firing with nothing there is a reliable "community is gone"
                // signal, not just an emptied member list.
                if (!snapshot.exists()) {
                    _members.value = emptyList()
                    _activeRiders.value = emptyList()
                    _wasDeleted.value = true
                    return
                }
                val all = mutableListOf<CommunityMember>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    if (id == myID) continue
                    val data = child.value as? Map<*, *> ?: continue
                    val name = data["name"] as? String ?: continue
                    all.add(
                        CommunityMember(
                            id = id,
                            name = name,
                            initials = data["initials"] as? String ?: name.take(2).uppercase(),
                            isRiding = data["isRiding"] as? Boolean ?: false,
                            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                            speed = (data["speed"] as? Number)?.toDouble() ?: 0.0,
                            lastSeen = (data["lastSeen"] as? Number)?.toDouble() ?: 0.0,
                            authUID = data["authUID"] as? String ?: ""
                        )
                    )
                }
                _members.value = all
                _activeRiders.value = all.filter { it.isRiding }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(l)
        membersListener = l
    }

    private fun stopListeningForMembers() {
        membersListener?.let {
            db.child("communities").child(communityID).child("members").removeEventListener(it)
        }
        membersListener = null
    }

    fun updateRidingStatus(isRiding: Boolean, location: Location? = null, speed: Double = 0.0) {
        val updates = mutableMapOf<String, Any>(
            "isRiding" to isRiding,
            "speed" to speed,
            "lastSeen" to System.currentTimeMillis() / 1000.0,
            "authUID" to (FirebaseAuth.getInstance().currentUser?.uid ?: "")
        )
        if (location != null) {
            updates["latitude"] = location.latitude
            updates["longitude"] = location.longitude
        }
        db.child("communities").child(communityID).child("members").child(myID).updateChildren(updates)
    }

    fun leaveCommunity() {
        stopListeningForMembers()
        val updates = mutableMapOf<String, Any?>(
            "communities/$communityID/members/$myID" to null
        )
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            updates["users/$uid/communityMemberships/$communityID"] = null
        }
        db.updateChildren(updates)
    }

    /** Creator-only: removes the community entirely for everyone, not just this device. */
    fun deleteCommunity() {
        if (myID.isEmpty() || !isCreator) return
        stopListeningForMembers()
        db.child("communities").child(communityID).removeValue()
    }
}
