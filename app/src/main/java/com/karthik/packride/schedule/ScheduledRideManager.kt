package com.karthik.packride.schedule

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.group.GroupRideFirebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ScheduledRide(
    val id: String,
    val rideCode: String,
    val title: String,
    val description: String,
    val creatorID: String,
    val creatorName: String,
    val creatorInitials: String,
    val scheduledDate: Double,
    val createdAt: Double,
    val meetupLocation: String,
    val meetupLat: Double,
    val meetupLng: Double,
    val communityID: String? = null,
    val rsvpCount: Int = 0
)

enum class RsvpStatus { GOING, MAYBE, NOT_GOING }

/** Firebase key each status is stored under — matches iOS's RSVPStatus rawValue exactly. */
private fun RsvpStatus.key(): String = when (this) {
    RsvpStatus.GOING -> "going"
    RsvpStatus.MAYBE -> "maybe"
    RsvpStatus.NOT_GOING -> "notGoing"
}

private fun rsvpStatusFromKey(key: String?): RsvpStatus? = when (key) {
    "going" -> RsvpStatus.GOING
    "maybe" -> RsvpStatus.MAYBE
    "notGoing" -> RsvpStatus.NOT_GOING
    else -> null
}

data class RsvpEntry(
    val id: String,
    val name: String,
    val initials: String,
    val status: RsvpStatus,
    val timestamp: Double
)

// Aug 30, 2026 — Schedule Ride visual+functionality parity pass: 1:1 port of
// iOS's ScheduledRideManager.swift RSVP + delete + community-share plumbing,
// which the earlier Aug 30 pass (WaypointsScreen's "Schedule" sheet) never
// added — that pass only needed create()/listenMine()/delete(id). This pass
// (ScheduleRideScreen, the My Rides tab under More) needs the rest: RSVPing
// to a ride, seeing who's coming, sharing a scheduled ride to a community,
// and deleting with community/user index cleanup. Originally deliberately
// NOT ported: listenForCommunityRides()/listenForUpcomingRides() (iOS's
// communityRides feed) — those back UpcomingRidesSection/
// CommunityScheduledRidesSection, which live on GroupRideView/
// CommunityDashboard, neither of which had its own parity pass yet.
//
// Aug 31, 2026 — GroupRideScreen's own parity pass (see GroupRideScreen.kt)
// is exactly that deferred work for the GroupRideView side: added
// listenForUpcomingRides()/communityRides below, a 1:1 port of iOS's
// ScheduledRideManager.listenForUpcomingRides() (all scheduled rides
// app-wide, ordered by scheduledDate, from now onward — note iOS's own
// method name is a slight misnomer, it does not actually filter by RSVP).
// listenForCommunityRides()/CommunityScheduledRidesSection are still NOT
// ported — that's CommunityDashboard's parity pass, not this one.
class ScheduledRideManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference

    private val _myScheduledRides = MutableStateFlow<List<ScheduledRide>>(emptyList())
    val myScheduledRides: StateFlow<List<ScheduledRide>> = _myScheduledRides.asStateFlow()

    // Aug 31, 2026 — backs GroupRideScreen's Upcoming Rides section. Named
    // communityRides to match iOS's ScheduledRideManager.swift @Published
    // var of the same name (both listenForCommunityRides() and
    // listenForUpcomingRides() populate it there; only the latter is ported
    // here so far — see this class's file header).
    private val _communityRides = MutableStateFlow<List<ScheduledRide>>(emptyList())
    val communityRides: StateFlow<List<ScheduledRide>> = _communityRides.asStateFlow()

    private val _rsvpList = MutableStateFlow<List<RsvpEntry>>(emptyList())
    val rsvpList: StateFlow<List<RsvpEntry>> = _rsvpList.asStateFlow()

    private var mineQuery: Query? = null
    private var mineListener: ValueEventListener? = null

    private var upcomingQuery: Query? = null
    private var upcomingListener: ValueEventListener? = null

    private var rsvpsRef: DatabaseReference? = null
    private var rsvpsListener: ValueEventListener? = null

    val myID: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: ""

    /** Convenience for the UI: my own RSVP status within whatever ride's RSVPs are currently loaded. */
    fun myRsvpStatus(): RsvpStatus? = _rsvpList.value.firstOrNull { it.id == myID }?.status

    // Aug 30, 2026 — optional rideCode param: WaypointsScreen's Schedule
    // sheet calls promoteToRideCode() first (which mints/reuses the SAME
    // code the planned waypoints are already published under via
    // GroupWaypointSync) and passes it in here, so the scheduled ride opens
    // straight into the pre-planned route instead of a second, empty code.
    fun create(
        title: String,
        description: String,
        scheduledDateMs: Long,
        meetupLocation: String,
        meetupLat: Double,
        meetupLng: Double,
        creatorName: String,
        rideCode: String? = null,
        communityID: String? = null,
        onDone: (Boolean) -> Unit
    ) {
        val me = myID
        if (me.isEmpty()) {
            onDone(false)
            return
        }
        val rideID = UUID.randomUUID().toString()
        val code = rideCode?.uppercase()?.takeIf { it.isNotBlank() } ?: GroupRideFirebase.generateRideCode()
        val initials = creatorName.rideInitials()
        val nowSec = System.currentTimeMillis() / 1000.0
        val data = mapOf(
            "id" to rideID,
            "rideCode" to code,
            "title" to title,
            "description" to description,
            "creatorID" to me,
            "creatorName" to creatorName,
            "creatorInitials" to initials,
            "scheduledDate" to scheduledDateMs / 1000.0,
            "createdAt" to nowSec,
            // Aug 30, 2026 — renamed from meetupLat/meetupLng (Android-only
            // keys nothing on iOS ever wrote) to iOS's actual schema keys, so
            // a ride's meetup point round-trips correctly regardless of which
            // platform created or is reading it.
            "meetupLocation" to meetupLocation,
            "meetupLatitude" to meetupLat,
            "meetupLongitude" to meetupLng,
            "communityID" to (communityID ?: ""),
            "rsvpCount" to 1
        )
        val rsvpData = mapOf(
            "name" to creatorName,
            "initials" to initials,
            "status" to RsvpStatus.GOING.key(),
            "timestamp" to nowSec
        )
        val rideWithRsvp = data + ("rsvps" to mapOf(me to rsvpData))
        val updates = mutableMapOf<String, Any?>(
            "scheduledRides/$rideID" to rideWithRsvp,
            "users/$me/scheduledRides/$rideID" to true
        )
        if (!communityID.isNullOrBlank()) updates["communities/$communityID/scheduledRides/$rideID"] = true
        db.updateChildren(updates)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun listenMine() {
        stopMine()
        val me = myID
        if (me.isEmpty()) return
        val query = db.child("scheduledRides").orderByChild("creatorID").equalTo(me)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _myScheduledRides.value = snapshot.children.mapNotNull { parse(it) }
                    .sortedBy { it.scheduledDate }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        query.addValueEventListener(l)
        mineQuery = query
        mineListener = l
    }

    private fun stopMine() {
        mineListener?.let { mineQuery?.removeEventListener(it) }
        mineQuery = null
        mineListener = null
    }

    /**
     * All scheduled rides app-wide, from now onward, ordered by
     * scheduledDate — 1:1 port of iOS's ScheduledRideManager.listenForUpcomingRides().
     * Backs GroupRideScreen's Upcoming Rides section (see GroupRideScreen.kt).
     */
    fun listenForUpcomingRides() {
        stopUpcoming()
        if (myID.isEmpty()) return
        val nowSec = System.currentTimeMillis() / 1000.0
        // Group's landing page renders only three items. Keep a modest buffer
        // without downloading an unbounded history of nested RSVP records.
        val query = db.child("scheduledRides").orderByChild("scheduledDate")
            .startAt(nowSec)
            .limitToFirst(25)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _communityRides.value = snapshot.children.mapNotNull { parse(it) }
                    .sortedBy { it.scheduledDate }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        query.addValueEventListener(l)
        upcomingQuery = query
        upcomingListener = l
    }

    /** Resolve the community's ride-ID index to live shared ride records, matching iOS. */
    fun listenForCommunityRides(communityID: String) {
        stopUpcoming()
        if (communityID.isBlank()) return
        val query = db.child("communities").child(communityID).child("scheduledRides")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }
                if (ids.isEmpty()) {
                    _communityRides.value = emptyList()
                    return
                }
                val loaded = mutableListOf<ScheduledRide>()
                var remaining = ids.size
                ids.forEach { id ->
                    db.child("scheduledRides").child(id).get().addOnCompleteListener { task ->
                        task.result?.let { parse(it) }?.let { loaded.add(it) }
                        remaining--
                        if (remaining == 0) {
                            val now = System.currentTimeMillis() / 1000.0
                            _communityRides.value = loaded.filter { it.scheduledDate >= now }.sortedBy { it.scheduledDate }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        query.addValueEventListener(l)
        upcomingQuery = query
        upcomingListener = l
    }

    private fun stopUpcoming() {
        upcomingListener?.let { upcomingQuery?.removeEventListener(it) }
        upcomingQuery = null
        upcomingListener = null
    }

    /** Live list of everyone's RSVP for one ride — call when opening a ride's detail, stopRSVPs() when leaving it. */
    fun loadRSVPs(rideId: String) {
        stopRSVPs()
        val ref = db.child("scheduledRides").child(rideId).child("rsvps")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _rsvpList.value = snapshot.children.mapNotNull { child ->
                    val d = child.value as? Map<*, *> ?: return@mapNotNull null
                    val name = d["name"] as? String ?: return@mapNotNull null
                    val status = rsvpStatusFromKey(d["status"] as? String) ?: return@mapNotNull null
                    RsvpEntry(
                        id = child.key ?: return@mapNotNull null,
                        name = name,
                        initials = d["initials"] as? String ?: name.rideInitials(),
                        status = status,
                        timestamp = (d["timestamp"] as? Number)?.toDouble() ?: 0.0
                    )
                }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(l)
        rsvpsRef = ref
        rsvpsListener = l
    }

    fun stopRSVPs() {
        rsvpsListener?.let { rsvpsRef?.removeEventListener(it) }
        rsvpsRef = null
        rsvpsListener = null
        _rsvpList.value = emptyList()
    }

    fun rsvp(rideId: String, name: String, status: RsvpStatus) {
        val me = myID
        if (me.isEmpty()) return
        val data = mapOf(
            "name" to name,
            "initials" to name.rideInitials(),
            "status" to status.key(),
            "timestamp" to System.currentTimeMillis() / 1000.0
        )
        db.child("scheduledRides").child(rideId).child("rsvps").child(me).setValue(data)
            .addOnSuccessListener { updateRsvpCountAtomically(rideId) }
    }

    fun cancelRSVP(rideId: String) {
        val me = myID
        if (me.isEmpty()) return
        db.child("scheduledRides").child(rideId).child("rsvps").child(me).removeValue()
            .addOnSuccessListener { updateRsvpCountAtomically(rideId) }
    }

    private fun updateRsvpCountAtomically(rideId: String) {
        val rideRef = db.child("scheduledRides").child(rideId)
        rideRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value == null) return Transaction.success(currentData)
                val count = currentData.child("rsvps").children.count { child ->
                    child.child("status").getValue(String::class.java) == RsvpStatus.GOING.key()
                }
                currentData.child("rsvpCount").value = count
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) = Unit
        })
    }

    fun deleteRide(rideId: String, communityID: String?) {
        val updates = mutableMapOf<String, Any?>("scheduledRides/$rideId" to null)
        if (!communityID.isNullOrBlank()) updates["communities/$communityID/scheduledRides/$rideId"] = null
        val me = myID
        if (me.isNotEmpty()) updates["users/$me/scheduledRides/$rideId"] = null
        // One multipath update prevents an offline/reconnected client from
        // observing an orphaned user/community index after the ride itself
        // has already disappeared (or vice versa).
        db.updateChildren(updates)
    }

    fun stop() {
        stopMine()
        stopUpcoming()
        stopRSVPs()
    }

    private fun parse(snap: DataSnapshot): ScheduledRide? {
        val d = snap.value as? Map<*, *> ?: return null
        return ScheduledRide(
            id = d["id"] as? String ?: snap.key ?: return null,
            rideCode = d["rideCode"] as? String ?: "",
            title = d["title"] as? String ?: return null,
            description = d["description"] as? String ?: "",
            creatorID = d["creatorID"] as? String ?: "",
            creatorName = d["creatorName"] as? String ?: "",
            creatorInitials = d["creatorInitials"] as? String ?: "?",
            scheduledDate = (d["scheduledDate"] as? Number)?.toDouble() ?: 0.0,
            createdAt = (d["createdAt"] as? Number)?.toDouble() ?: 0.0,
            meetupLocation = d["meetupLocation"] as? String ?: "",
            meetupLat = (d["meetupLatitude"] as? Number)?.toDouble() ?: 0.0,
            meetupLng = (d["meetupLongitude"] as? Number)?.toDouble() ?: 0.0,
            communityID = (d["communityID"] as? String)?.takeIf { it.isNotBlank() },
            rsvpCount = (d["rsvpCount"] as? Number)?.toInt() ?: 0
        )
    }
}
