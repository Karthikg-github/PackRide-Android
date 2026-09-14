package com.karthik.packride.friends

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RiderProfile(
    val id: String,
    val name: String,
    val initials: String,
    val bike: String = "",
    val city: String = "",
    val avatarURL: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val isOnline: Boolean = false,
    val experience: String = ""
)

data class FollowRequest(
    val id: String,
    val name: String,
    val initials: String,
    val timestamp: Double
)

/**
 * Following graph + live locations — closer to iOS UserProfileManager / FriendsMapView.
 */
class FriendsManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference

    private val _followedUsers = MutableStateFlow<List<RiderProfile>>(emptyList())
    val followedUsers: StateFlow<List<RiderProfile>> = _followedUsers.asStateFlow()

    private val _discover = MutableStateFlow<List<RiderProfile>>(emptyList())
    val discover: StateFlow<List<RiderProfile>> = _discover.asStateFlow()

    private val _followers = MutableStateFlow<List<RiderProfile>>(emptyList())
    val followers: StateFlow<List<RiderProfile>> = _followers.asStateFlow()

    private val _followRequests = MutableStateFlow<List<FollowRequest>>(emptyList())
    val followRequests: StateFlow<List<FollowRequest>> = _followRequests.asStateFlow()

    private val _requestedIds = MutableStateFlow<Set<String>>(emptySet())
    val requestedIds: StateFlow<Set<String>> = _requestedIds.asStateFlow()

    private val privateLocations = MutableStateFlow<Map<String, Pair<Double, Double>>>(emptyMap())

    private var followingListener: ValueEventListener? = null
    private var followersListener: ValueEventListener? = null
    private var usersListener: ValueEventListener? = null
    private var followRequestsListener: ValueEventListener? = null
    private var privateLocationsListener: ValueEventListener? = null
    private var blockedUsersListener: ValueEventListener? = null
    private var blockedIDs: Set<String> = emptySet()
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    val myID: String
        get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun start() {
        if (authStateListener == null) {
            authStateListener = FirebaseAuth.AuthStateListener { auth ->
                // A manager can be composed during Firebase Auth's restore
                // window. Previously start() returned with an empty/device
                // id and never rebound, so iOS requests under the account UID
                // remained invisible until the screen was recreated.
                if (auth.currentUser != null && followRequestsListener == null) {
                    listenBlockedUsers()
                    listenFollowing()
                    listenFollowers()
                    listenFollowRequests()
                    listenPrivateLocations()
                    listenDiscover()
                }
            }.also { FirebaseAuth.getInstance().addAuthStateListener(it) }
        }
        listenBlockedUsers()
        listenFollowing()
        listenFollowers()
        listenFollowRequests()
        listenPrivateLocations()
        listenDiscover()
    }

    /**
     * Profile only needs the follower list for its location-audience picker.
     * Do not attach the discover listener here: that listener reads the complete
     * /users tree and can make opening Profile exhaust memory on a real account.
     */
    fun startFollowersOnly() {
        listenBlockedUsers()
        listenFollowing()
        listenFollowers()
        listenFollowRequests()
    }

    fun stop() {
        authStateListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        authStateListener = null
        followingListener?.let {
            if (myID.isNotEmpty()) db.child("users").child(myID).child("following").removeEventListener(it)
        }
        followingListener = null
        followersListener?.let {
            if (myID.isNotEmpty()) db.child("users").child(myID).child("followers").removeEventListener(it)
        }
        followersListener = null
        usersListener?.let { db.child("publicRiders").removeEventListener(it) }
        usersListener = null
        followRequestsListener?.let {
            if (myID.isNotEmpty()) db.child("users").child(myID).child("followRequests").removeEventListener(it)
        }
        followRequestsListener = null
        privateLocationsListener?.let {
            if (myID.isNotEmpty()) db.child("locationFeeds").child(myID).removeEventListener(it)
        }
        privateLocationsListener = null
        blockedUsersListener?.let {
            if (myID.isNotEmpty()) db.child("users").child(myID).child("blockedUsers").removeEventListener(it)
        }
        blockedUsersListener = null
    }

    fun sendFollowRequest(targetUserID: String, myName: String, myInitials: String) {
        val me = myID
        if (me.isEmpty() || targetUserID.isEmpty() || me == targetUserID || targetUserID in blockedIDs) return
        val request = mapOf(
            "name" to myName,
            "initials" to myInitials,
            "timestamp" to (System.currentTimeMillis() / 1000.0)
        )
        db.child("users").child(targetUserID).child("followRequests").child(me)
            .setValue(request)
            .addOnSuccessListener { _requestedIds.value = _requestedIds.value + targetUserID }
    }

    fun acceptFollowRequest(requesterID: String, onResult: (String?) -> Unit = {}) {
        val me = myID
        if (me.isEmpty()) {
            onResult("You need to be signed in to approve a follow request.")
            return
        }
        db.updateChildren(
            mapOf(
                "users/$me/followers/$requesterID" to true,
                "users/$requesterID/following/$me" to true,
                "users/$me/followRequests/$requesterID" to null
            )
        ).addOnCompleteListener { task -> onResult(task.exception?.localizedMessage) }
    }

    fun declineFollowRequest(requesterID: String) {
        val me = myID
        if (me.isEmpty()) return
        db.child("users").child(me).child("followRequests").child(requesterID).removeValue()
    }

    fun removeFollower(userID: String) {
        val me = myID
        if (me.isEmpty()) return
        db.updateChildren(
            mapOf(
                "users/$me/followers/$userID" to null,
                "users/$userID/following/$me" to null
            )
        )
    }

    fun unfollow(targetUserID: String) {
        val me = myID
        if (me.isEmpty() || targetUserID.isEmpty()) return
        db.child("users").child(me).child("following").child(targetUserID).removeValue()
        db.child("users").child(targetUserID).child("followers").child(me).removeValue()
    }

    /** Publish my location for Friends map (same path style as iOS). */
    fun publishMyLocation(location: Location) {
        val me = myID
        if (me.isEmpty()) return
        val data = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "updatedAt" to ServerValue.TIMESTAMP,
            "isOnline" to true
        )
        db.child("users").child(me).child("location").updateChildren(data)
    }

    fun setOnline(online: Boolean) {
        val me = myID
        if (me.isEmpty()) return
        db.child("users").child(me).child("location").child("isOnline").setValue(online)
        db.child("users").child(me).child("profile").child("isOnline").setValue(online)
    }

    fun followingIds(): List<String> = _followedUsers.value.map { it.id }

    private fun listenBlockedUsers() {
        val me = myID
        if (me.isEmpty() || blockedUsersListener != null) return
        val ref = db.child("users").child(me).child("blockedUsers")
        blockedUsersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                blockedIDs = snapshot.children.mapNotNull { it.key }.toSet()
                _followedUsers.value = _followedUsers.value.filterNot { it.id in blockedIDs }
                _followers.value = _followers.value.filterNot { it.id in blockedIDs }
                _discover.value = _discover.value.filterNot { it.id in blockedIDs }
                _followRequests.value = _followRequests.value.filterNot { it.id in blockedIDs }
                privateLocations.value = privateLocations.value.filterKeys { it !in blockedIDs }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }.also { ref.addValueEventListener(it) }
    }

    private fun listenFollowing() {
        val me = myID
        if (me.isEmpty()) return
        followingListener?.let {
            db.child("users").child(me).child("following").removeEventListener(it)
        }
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.filterNot { it in blockedIDs }
                loadProfiles(ids) { _followedUsers.value = it }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        db.child("users").child(me).child("following").addValueEventListener(l)
        followingListener = l
    }

    private fun listenFollowers() {
        val me = myID
        if (me.isEmpty()) return
        followersListener?.let {
            db.child("users").child(me).child("followers").removeEventListener(it)
        }
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.filterNot { it in blockedIDs }
                loadProfiles(ids) { _followers.value = it }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        db.child("users").child(me).child("followers").addValueEventListener(l)
        followersListener = l
    }

    private fun listenDiscover() {
        usersListener?.let { db.child("publicRiders").removeEventListener(it) }
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val me = myID
                val following = _followedUsers.value.map { it.id }.toSet()
                val list = mutableListOf<RiderProfile>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    if (id == me || following.contains(id) || id in blockedIDs) continue
                    parsePublicProfile(id, child)?.let { list.add(it) }
                }
                _discover.value = list.sortedBy { it.name.lowercase() }.take(50)
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        // iOS uses /publicRiders for discovery. /users contains private and
        // potentially very large nested account data and must never be used
        // as a directory/list screen source.
        db.child("publicRiders").addValueEventListener(l)
        usersListener = l
    }

    private fun listenFollowRequests() {
        val me = myID
        if (me.isEmpty()) return
        followRequestsListener?.let {
            db.child("users").child(me).child("followRequests").removeEventListener(it)
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _followRequests.value = snapshot.children.mapNotNull { child ->
                    if (child.key in blockedIDs) return@mapNotNull null
                    val name = child.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    FollowRequest(
                        id = child.key ?: return@mapNotNull null,
                        name = name,
                        initials = child.child("initials").getValue(String::class.java)
                            ?: name.take(2).uppercase(),
                        timestamp = (child.child("timestamp").value as? Number)?.toDouble() ?: 0.0
                    )
                }.sortedByDescending { it.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Follow request listener cancelled for uid=$me", error.toException())
            }
        }
        db.child("users").child(me).child("followRequests").addValueEventListener(listener)
        followRequestsListener = listener
    }

    private fun loadProfiles(ids: List<String>, onResult: (List<RiderProfile>) -> Unit) {
        val visibleIds = ids.filterNot { it in blockedIDs }
        if (visibleIds.isEmpty()) {
            onResult(emptyList())
            return
        }
        val result = mutableListOf<RiderProfile>()
        var pending = visibleIds.size
        visibleIds.forEach { id ->
            // Only fetch the small profile node. A full users/{id} read also
            // downloads that rider's posts, rides, follows and other nested
            // data, which can exhaust memory when opening a follower picker.
            db.child("users").child(id).child("profile").get().addOnSuccessListener { snap ->
                parseProfileNode(id, snap)?.let { profile ->
                    val shared = privateLocations.value[id]
                    result.add(profile.copy(lat = shared?.first, lng = shared?.second, isOnline = shared != null))
                }
                pending--
                if (pending <= 0) onResult(result.sortedBy { it.name.lowercase() })
            }.addOnFailureListener {
                pending--
                if (pending <= 0) onResult(result.sortedBy { it.name.lowercase() })
            }
        }
    }

    private fun listenPrivateLocations() {
        val me = myID
        if (me.isEmpty()) return
        privateLocationsListener?.let { db.child("locationFeeds").child(me).removeEventListener(it) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    if (id in blockedIDs) return@mapNotNull null
                    val lat = (child.child("latitude").value as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng = (child.child("longitude").value as? Number)?.toDouble() ?: return@mapNotNull null
                    id to (lat to lng)
                }.toMap()
                privateLocations.value = locations
                _followedUsers.value = _followedUsers.value.map { profile ->
                    val shared = locations[profile.id]
                    profile.copy(lat = shared?.first, lng = shared?.second, isOnline = shared != null)
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        db.child("locationFeeds").child(me).addValueEventListener(listener)
        privateLocationsListener = listener
    }

    private fun parseProfile(id: String, snap: DataSnapshot): RiderProfile? {
        val profile = snap.child("profile").value as? Map<*, *> ?: return null
        val name = profile["name"] as? String ?: return null
        if (name.isBlank()) return null
        val loc = snap.child("location").value as? Map<*, *>
        return RiderProfile(
            id = id,
            name = name,
            initials = profile["initials"] as? String ?: name.take(2).uppercase(),
            bike = profile["bike"] as? String ?: "",
            city = profile["city"] as? String ?: "",
            avatarURL = profile["avatarURL"] as? String ?: "",
            lat = (loc?.get("latitude") as? Number)?.toDouble(),
            lng = (loc?.get("longitude") as? Number)?.toDouble(),
            isOnline = (loc?.get("isOnline") as? Boolean)
                ?: (profile["isOnline"] as? Boolean)
                ?: false,
            experience = profile["experience"] as? String ?: ""
        )
    }

    private fun parseProfileNode(id: String, profile: DataSnapshot): RiderProfile? {
        val name = profile.child("name").getValue(String::class.java) ?: return null
        if (name.isBlank()) return null
        return RiderProfile(
            id = id,
            name = name,
            initials = profile.child("initials").getValue(String::class.java) ?: name.take(2).uppercase(),
            bike = profile.child("bike").getValue(String::class.java).orEmpty(),
            city = profile.child("city").getValue(String::class.java).orEmpty(),
            avatarURL = profile.child("avatarURL").getValue(String::class.java).orEmpty(),
            experience = profile.child("experience").getValue(String::class.java).orEmpty()
        )
    }

    private fun parsePublicProfile(id: String, profile: DataSnapshot): RiderProfile? {
        val name = profile.child("name").getValue(String::class.java) ?: return null
        if (name.isBlank()) return null
        return RiderProfile(
            id = id,
            name = name,
            initials = profile.child("initials").getValue(String::class.java) ?: name.take(2).uppercase(),
            bike = profile.child("bike").getValue(String::class.java).orEmpty(),
            city = profile.child("city").getValue(String::class.java).orEmpty(),
            avatarURL = profile.child("avatarURL").getValue(String::class.java).orEmpty(),
            experience = profile.child("experience").getValue(String::class.java).orEmpty()
        )
    }

    companion object {
        private const val TAG = "PackRideFriends"
    }
}
