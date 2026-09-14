package com.karthik.packride.community

import android.content.Context
import android.util.Log
import android.location.Location
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-wide "which private communities has this device joined or created" —
 * Aug 30, 2026 rewrite: now the real source of truth for creating/joining
 * communities (was previously just a locally-cached mirror of whatever the
 * old public-directory CommunityManager already created/joined with no
 * passcode at all — see CommunityManager.kt's file header). 1:1 port of
 * iOS's CommunityMembershipStore: createCommunity() generates a unique
 * 4-character join code with collision retry, joinCommunity() validates the
 * passcode against Firebase before adding it locally. Same known tradeoff
 * as iOS, with a Firebase reverse index used to restore memberships after a
 * reinstall or login on another device.
 */
class CommunityMembershipStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("packride_communities", Context.MODE_PRIVATE)
    private val db = FirebaseDatabase.getInstance().reference

    private val myID: String
        get() = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: ""

    private val _myCommunities = MutableStateFlow(load())
    val myCommunities: StateFlow<List<Community>> = _myCommunities.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    fun add(community: Community) {
        if (_myCommunities.value.any { it.id == community.id }) return
        _myCommunities.value = _myCommunities.value + community
        save()
    }

    fun remove(id: String) {
        _myCommunities.value = _myCommunities.value.filter { it.id != id }
        save()
    }

    fun createCommunity(name: String, passcode: String, riderName: String, onDone: (String?) -> Unit) {
        if (name.isBlank() || passcode.isBlank()) {
            _errorMessage.value = "Please fill in all fields"
            onDone(null)
            return
        }
        _isLoading.value = true
        _errorMessage.value = ""
        // iOS (CommunityView.attemptCreateCommunity) stores the passcode exactly
        // as the user typed it -- no case transform -- so match that here.
        attemptCreate(name.trim(), passcode.trim(), riderName, attemptsLeft = 5, onDone = onDone)
    }

    private fun attemptCreate(name: String, passcode: String, riderName: String, attemptsLeft: Int, onDone: (String?) -> Unit) {
        val id = generateJoinCode()
        db.child("communities").child(id).get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                if (attemptsLeft <= 0) {
                    _isLoading.value = false
                    _errorMessage.value = "Couldn't generate a unique community code — try again."
                    onDone(null)
                    return@addOnSuccessListener
                }
                attemptCreate(name, passcode, riderName, attemptsLeft - 1, onDone)
                return@addOnSuccessListener
            }
            val creator = myID
            val community = Community(
                id = id, name = name, passcode = passcode, createdBy = creator,
                memberCount = 1, createdAt = System.currentTimeMillis() / 1000.0
            )
            val data = mapOf(
                "id" to community.id, "name" to community.name, "passcode" to community.passcode,
                "createdBy" to community.createdBy, "memberCount" to community.memberCount,
                "createdAt" to community.createdAt
            )
            db.child("communities").child(id).setValue(data)
                .addOnSuccessListener {
                    joinMemberRecord(id, riderName)
                    add(community)
                    _isLoading.value = false
                    onDone(id)
                }
                .addOnFailureListener {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to create community"
                    onDone(null)
                }
        }.addOnFailureListener {
            _isLoading.value = false
            _errorMessage.value = "Failed to create community"
            onDone(null)
        }
    }

    fun joinCommunity(id: String, passcode: String, riderName: String, onDone: (String?) -> Unit) {
        val trimmedId = id.trim().uppercase()
        // iOS (CommunityView.joinCommunity) compares `storedPasscode == passcode`
        // case-sensitively, with no uppercasing -- match that here so a
        // mixed-/lower-case passcode created on either platform still joins.
        val trimmedPasscode = passcode.trim()
        if (trimmedId.isBlank() || trimmedPasscode.isBlank()) {
            _errorMessage.value = "Please fill in all fields"
            onDone(null)
            return
        }
        _isLoading.value = true
        _errorMessage.value = ""
        db.child("communities").child(trimmedId).get().addOnSuccessListener { snap ->
            _isLoading.value = false
            val data = snap.value as? Map<*, *>
            val storedPasscode = data?.get("passcode") as? String
            if (data == null || storedPasscode != trimmedPasscode) {
                _errorMessage.value = "Invalid community ID or passcode"
                onDone(null)
                return@addOnSuccessListener
            }
            val community = Community(
                id = trimmedId,
                name = data["name"] as? String ?: "Community",
                passcode = trimmedPasscode,
                createdBy = data["createdBy"] as? String ?: "",
                memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
                createdAt = (data["createdAt"] as? Number)?.toDouble() ?: 0.0
            )
            joinMemberRecord(trimmedId, riderName)
            add(community)
            onDone(trimmedId)
        }.addOnFailureListener {
            _isLoading.value = false
            _errorMessage.value = "Invalid community ID or passcode"
            onDone(null)
        }
    }

    /** Writes this device's own member record — shared by create and join, since creating also makes you the first member. */
    private fun joinMemberRecord(communityId: String, riderName: String) {
        val data = mapOf(
            "id" to myID, "name" to riderName, "initials" to riderName.rideInitials(),
            "authUID" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "isRiding" to false, "latitude" to 0.0, "longitude" to 0.0, "speed" to 0.0,
            "lastSeen" to (System.currentTimeMillis() / 1000.0),
            "fcmToken" to cachedFcmToken()
        )
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val updates = mutableMapOf<String, Any?>(
            "communities/$communityId/members/$myID" to data
        )
        if (!uid.isNullOrEmpty()) updates["users/$uid/communityMemberships/$communityId"] = true
        db.updateChildren(updates)
    }

    /** Restores cloud memberships and repairs this device's member records after login. */
    fun syncCurrentIdentity() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repairMemberships(_myCommunities.value, uid)
        db.child("users").child(uid).child("communityMemberships").get()
            .addOnSuccessListener { index ->
                Log.d(TAG, "syncCurrentIdentity: reverse index has ${index.childrenCount} communities for uid=$uid")
                index.children.mapNotNull { it.key }.forEach { communityId ->
                    db.child("communities").child(communityId).get()
                        .addOnSuccessListener { snapshot ->
                            val data = snapshot.value as? Map<*, *>
                            if (data == null) {
                                Log.w(TAG, "syncCurrentIdentity: community $communityId in reverse index but node missing/unreadable")
                                return@addOnSuccessListener
                            }
                            val community = communityFromMap(communityId, data)
                            _myCommunities.value = _myCommunities.value.filterNot { it.id == communityId } + community
                            save()
                            repairMemberRecord(communityId, uid)
                        }
                        .addOnFailureListener { e ->
                            // Sep 3, 2026 — Karthik-reported bug: a joined-on-iOS
                            // community wasn't showing up on Android under the
                            // same account. This read (and the reverse-index read
                            // above) previously had no failure handling at all, so
                            // any permission-denied/network error here failed
                            // completely silently — My Communities just stayed
                            // empty with nothing in Logcat to explain why. Filter
                            // Logcat for "PackRideSync" after signing in to see
                            // whether this is actually firing and why.
                            Log.e(TAG, "syncCurrentIdentity: failed reading community $communityId", e)
                        }
                }
                // Recover memberships created before the per-user reverse
                // index existed. Newer iOS records carry authUID even though
                // the member key is an installation id, so discover those
                // records once and backfill the shared index for both apps.
                discoverMembershipsByAuthUid(uid)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "syncCurrentIdentity: failed reading communityMemberships reverse index for uid=$uid", e)
            }
    }

    private fun discoverMembershipsByAuthUid(uid: String) {
        db.child("communities").get()
            .addOnSuccessListener { root ->
                root.children.forEach { communitySnapshot ->
                    val communityId = communitySnapshot.key ?: return@forEach
                    val isMember = communitySnapshot.child("members").children.any {
                        it.child("authUID").getValue(String::class.java) == uid
                    }
                    if (!isMember) return@forEach
                    val data = communitySnapshot.value as? Map<*, *> ?: return@forEach
                    val community = communityFromMap(communityId, data)
                    _myCommunities.value = _myCommunities.value.filterNot { it.id == communityId } + community
                    save()
                    repairMemberRecord(communityId, uid)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Legacy community discovery unavailable; reverse index remains authoritative", e)
            }
    }

    private fun repairMemberships(communities: List<Community>, uid: String) {
        communities.forEach { community ->
            db.child("communities").child(community.id).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    repairMemberRecord(community.id, uid)
                } else {
                    remove(community.id)
                    db.child("users").child(uid).child("communityMemberships").child(community.id).removeValue()
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "repairMemberships: failed reading community ${community.id}", e)
            }
        }
    }

    private fun repairMemberRecord(communityId: String, uid: String) {
        val currentRef = db.child("communities").child(communityId).child("members").child(myID)
        currentRef.get().addOnSuccessListener { current ->
            val existing = current.value as? Map<*, *> ?: emptyMap<Any, Any>()
            val legacyRef = db.child("communities").child(communityId).child("members").child(uid)
            legacyRef.get().addOnSuccessListener { legacy ->
                val legacyData = legacy.value as? Map<*, *> ?: emptyMap<Any, Any>()
                val source = existing.ifEmpty { legacyData }
                val riderName = UserPrefs(appContext).riderName.ifBlank {
                    source["name"] as? String ?: "Rider"
                }
                val member = mapOf(
                    "id" to myID,
                    "name" to riderName,
                    "initials" to riderName.rideInitials(),
                    "authUID" to uid,
                    "isRiding" to (source["isRiding"] ?: false),
                    "latitude" to (source["latitude"] ?: 0.0),
                    "longitude" to (source["longitude"] ?: 0.0),
                    "speed" to (source["speed"] ?: 0.0),
                    "lastSeen" to (source["lastSeen"] ?: System.currentTimeMillis() / 1000.0),
                    "fcmToken" to cachedFcmToken().ifEmpty { source["fcmToken"] as? String ?: "" }
                )
                val updates = mutableMapOf<String, Any?>(
                    "communities/$communityId/members/$myID" to member,
                    "users/$uid/communityMemberships/$communityId" to true
                )
                if (myID != uid && legacy.exists()) updates["communities/$communityId/members/$uid"] = null
                db.updateChildren(updates)
            }
        }
    }

    private fun communityFromMap(id: String, data: Map<*, *>): Community = Community(
        id = data["id"] as? String ?: id,
        name = data["name"] as? String ?: "Community",
        passcode = data["passcode"] as? String ?: "",
        createdBy = data["createdBy"] as? String ?: "",
        memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
        createdAt = (data["createdAt"] as? Number)?.toDouble() ?: 0.0
    )

    private fun cachedFcmToken(): String =
        appContext.getSharedPreferences("packride_prefs", Context.MODE_PRIVATE).getString("fcmToken", "") ?: ""

    /**
     * Fan out a freshly (re)issued FCM token to every community this device
     * has joined — port of iOS NotificationManager.saveTokenToFirebase's
     * community loop (called from PackRideMessagingService.onNewToken()).
     */
    fun writeFcmTokenToJoinedCommunities(token: String) {
        val id = myID
        if (id.isEmpty()) return
        _myCommunities.value.forEach { community ->
            db.child("communities").child(community.id).child("members").child(id)
                .child("fcmToken").setValue(token)
        }
    }

    /**
     * Flips `isRiding` on this device's membership record in every community
     * it opted to share this ride with — port of iOS ActiveSoloRideView's
     * setCommunityRidingStatus(), called from SoloRideSession at ride
     * start/end. A Cloud Function watches this exact field to notify each
     * community when someone starts riding.
     */
    fun setRidingStatus(isRiding: Boolean, communityIds: Set<String>) {
        if (communityIds.isEmpty()) return
        val id = myID
        if (id.isEmpty()) return
        val updates = mapOf(
            "isRiding" to isRiding,
            "lastSeen" to (System.currentTimeMillis() / 1000.0)
        )
        communityIds.forEach { communityId ->
            db.child("communities").child(communityId).child("members").child(id).updateChildren(updates)
        }
    }

    /**
     * Publishes a real ride position to every shared community — port of
     * iOS ActiveSoloRideView.updateCommunityLocation(). Caller (SoloRideSession)
     * applies the same 5s-or-25m throttle GroupRideFirebase uses.
     */
    fun updateRidingLocation(location: Location, speedMph: Double, communityIds: Set<String>) {
        if (communityIds.isEmpty()) return
        val id = myID
        if (id.isEmpty()) return
        val updates = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "speed" to speedMph,
            "lastSeen" to (System.currentTimeMillis() / 1000.0)
        )
        communityIds.forEach { communityId ->
            db.child("communities").child(communityId).child("members").child(id).updateChildren(updates)
        }
    }

    /** Same alphabet/shape as GroupRideFirebase.generateRideCode() (excludes ambiguous chars: no I/O/0/1), 4 chars — matches iOS's join-code length. */
    private fun generateJoinCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..4).map { alphabet.random() }.joinToString("")
    }

    private fun load(): List<Community> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Community(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    passcode = o.optString("passcode", ""),
                    createdBy = o.optString("createdBy", ""),
                    memberCount = o.optInt("memberCount", 0),
                    createdAt = o.optDouble("createdAt", 0.0)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val arr = JSONArray()
        _myCommunities.value.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("passcode", c.passcode)
                    put("createdBy", c.createdBy)
                    put("memberCount", c.memberCount)
                    put("createdAt", c.createdAt)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /**
     * Aug 22-parity self-heal, ported: every time the list itself is opened,
     * check each cached community against Firebase and quietly drop any that
     * no longer exist — otherwise a community deleted by its creator would
     * sit in My Communities forever on every other member's device, 404-ing
     * the moment it's tapped.
     */
    fun pruneDeletedCommunities() {
        _myCommunities.value.forEach { community ->
            db.child("communities").child(community.id).get().addOnSuccessListener { snap ->
                if (!snap.exists()) remove(community.id)
            }
        }
    }

    companion object {
        private const val TAG = "PackRideSync"
        private const val KEY = "myCommunitiesV2"

        @Volatile
        private var instance: CommunityMembershipStore? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = CommunityMembershipStore(context.applicationContext)
                    }
                }
            }
        }

        fun get(): CommunityMembershipStore =
            instance ?: error("Call CommunityMembershipStore.init(Application) first")
    }
}
