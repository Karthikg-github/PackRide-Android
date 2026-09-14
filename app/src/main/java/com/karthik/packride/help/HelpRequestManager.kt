package com.karthik.packride.help

import android.content.Context
import android.location.Location
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.karthik.packride.location.SharedLocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Need Help live share — port of iOS HelpRequestManager.
 * RTDB: helpRequests/{uid}
 * Background GPS reason: needHelp; throttle 5s / 25m.
 *
 * Aug 30, 2026 — app-wide singleton (was created fresh per-screen via
 * `remember{}` in NeedHelpScreen and torn down on navigation, same bug
 * CrashDetectionManager/SharedLocationManager already had fixed). A sharing
 * session — and the "who's currently sharing with me" listener the new
 * app-wide banner in PackRideNav depends on — both need to keep running
 * regardless of which screen is on top, exactly like iOS's @EnvironmentObject
 * instance owned once at the app level.
 */
class HelpRequestManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference
    private val locationManager = SharedLocationManager.get()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _activeRequests = MutableStateFlow<List<HelpRequest>>(emptyList())
    val activeRequests: StateFlow<List<HelpRequest>> = _activeRequests.asStateFlow()

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _sharingWithLabel = MutableStateFlow("")
    val sharingWithLabel: StateFlow<String> = _sharingWithLabel.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var locationJob: Job? = null
    private var listener: ValueEventListener? = null
    private var listenerRef: DatabaseReference? = null
    private var listenerRefCount = 0
    private var staleCleanupJob: Job? = null
    private var lastSent: Location? = null
    private var lastSentMs: Long = 0

    val myUID: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: ""

    val myDeviceID: String
        get() = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    fun start(
        targetType: String,
        targetID: String,
        targetName: String,
        requesterName: String,
        requesterInitials: String,
        latitude: Double,
        longitude: Double
    ) {
        val uid = myUID
        if (uid.isEmpty()) {
            _error.value = "Not signed in — try logging out and back in."
            return
        }
        _error.value = null
        val now = System.currentTimeMillis() / 1000.0
        val data = mapOf(
            "requesterUID" to uid,
            "requesterDeviceID" to myDeviceID,
            "requesterName" to requesterName,
            "requesterInitials" to requesterInitials,
            "latitude" to latitude,
            "longitude" to longitude,
            "startedAt" to now,
            "lastUpdated" to now,
            "targetType" to targetType,
            "targetID" to targetID,
            "targetName" to targetName
        )
        db.child("helpRequests").child(uid).setValue(data)
            .addOnSuccessListener {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_ACTIVE, true)
                    .putString(KEY_OWNER_UID, uid)
                    .putString(KEY_TARGET_TYPE, targetType)
                    .putString(KEY_TARGET_ID, targetID)
                    .putString(KEY_TARGET_NAME, targetName)
                    .putString(KEY_REQUESTER_NAME, requesterName)
                    .putString(KEY_REQUESTER_INITIALS, requesterInitials)
                    .putLong(KEY_STARTED_AT_BITS, java.lang.Double.doubleToRawLongBits(now))
                    .putLong(KEY_LATITUDE_BITS, java.lang.Double.doubleToRawLongBits(latitude))
                    .putLong(KEY_LONGITUDE_BITS, java.lang.Double.doubleToRawLongBits(longitude))
                    .apply()
                _isSharing.value = true
                _sharingWithLabel.value = targetName
                beginLocationUpdates()
            }
            .addOnFailureListener { e ->
                _error.value = e.localizedMessage ?: "Failed to start sharing"
            }
    }

    fun stop() {
        val uid = myUID
        if (uid.isNotEmpty()) {
            db.child("helpRequests").child(uid).removeValue()
        }
        _isSharing.value = false
        _sharingWithLabel.value = ""
        lastSent = null
        lastSentMs = 0
        locationJob?.cancel()
        locationJob = null
        locationManager.releaseBackgroundUpdates(SharedLocationManager.REASON_NEED_HELP)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .clear().apply()
    }

    /** Reconnect an explicitly active Need Help share after Android recreates the app process. */
    fun restoreIfNeeded() {
        if (_isSharing.value || !shouldRestore(appContext)) return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = myUID
        if (uid.isEmpty()) return
        if (prefs.getString(KEY_OWNER_UID, null) != uid) {
            prefs.edit().clear().apply()
            return
        }
        val targetType = prefs.getString(KEY_TARGET_TYPE, null) ?: return
        val targetID = prefs.getString(KEY_TARGET_ID, null) ?: return
        val targetName = prefs.getString(KEY_TARGET_NAME, "Safety contacts").orEmpty()
        val requesterName = prefs.getString(KEY_REQUESTER_NAME, "A PackRide rider").orEmpty()
        val requesterInitials = prefs.getString(KEY_REQUESTER_INITIALS, "?").orEmpty()
        val startedAt = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_STARTED_AT_BITS, 0L))
            .takeIf { it.isFinite() && it > 0 } ?: (System.currentTimeMillis() / 1000.0)
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LATITUDE_BITS, 0L))
        val lng = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LONGITUDE_BITS, 0L))
        val now = System.currentTimeMillis() / 1000.0
        // Reassert the complete record rather than only sending coordinate
        // patches. This is safe online and queues offline, and prevents a
        // process killed between restores from creating a malformed partial
        // emergency request.
        db.child("helpRequests").child(uid).updateChildren(mapOf(
            "requesterUID" to uid,
            "requesterDeviceID" to myDeviceID,
            "requesterName" to requesterName,
            "requesterInitials" to requesterInitials,
            "latitude" to lat,
            "longitude" to lng,
            "startedAt" to startedAt,
            "lastUpdated" to now,
            "targetType" to targetType,
            "targetID" to targetID,
            "targetName" to targetName
        ))
        _sharingWithLabel.value = targetName
        _isSharing.value = true
        beginLocationUpdates()
    }

    // Aug 30, 2026 — ref-counted the same way SharedLocationManager's start/
    // stop reasons are, now that this manager is app-wide: both NeedHelpScreen
    // and PackRideNav's root banner call listenForActiveRequests()/
    // stopListening() independently (screen open + close, app foreground +
    // background), and neither should tear down the other's listener.
    fun listenForActiveRequests() {
        listenerRefCount++
        if (listener != null) return
        // Recipient-specific fan-out written by the shared Cloud Function.
        // Never subscribe Android clients to the global helpRequests tree:
        // it exposes every rider's emergency coordinates and diverges from iOS.
        val uid = myUID
        if (uid.isEmpty()) return
        val ref = db.child("users").child(uid).child("helpAlerts")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<HelpRequest>()
                for (child in snapshot.children) {
                    val data = child.value as? Map<*, *> ?: continue
                    try {
                        val req = HelpRequest(
                            id = child.key ?: continue,
                            requesterUID = data["requesterUID"] as? String ?: continue,
                            requesterDeviceID = data["requesterDeviceID"] as? String ?: "",
                            requesterName = data["requesterName"] as? String ?: continue,
                            requesterInitials = data["requesterInitials"] as? String ?: "?",
                            latitude = (data["latitude"] as? Number)?.toDouble() ?: continue,
                            longitude = (data["longitude"] as? Number)?.toDouble() ?: continue,
                            startedAt = (data["startedAt"] as? Number)?.toDouble() ?: 0.0,
                            lastUpdated = (data["lastUpdated"] as? Number)?.toDouble() ?: 0.0,
                            targetType = data["targetType"] as? String ?: "friend",
                            targetID = data["targetID"] as? String ?: "",
                            targetName = data["targetName"] as? String ?: ""
                        )
                        if (!req.isStale) loaded.add(req)
                    } catch (_: Exception) {
                        continue
                    }
                }
                _activeRequests.value = loaded
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
            }
        }
        ref.addValueEventListener(l)
        listener = l
        listenerRef = ref
        staleCleanupJob?.cancel()
        staleCleanupJob = scope.launch {
            while (isActive) {
                delay(60_000)
                _activeRequests.value = _activeRequests.value.filterNot { it.isStale }
            }
        }
    }

    fun stopListening() {
        if (listenerRefCount > 0) listenerRefCount--
        if (listenerRefCount > 0) return
        listener?.let { listenerRef?.removeEventListener(it) }
        listener = null
        listenerRef = null
        staleCleanupJob?.cancel()
        staleCleanupJob = null
        // Do not retain a previous account's private safety inbox in the
        // app-wide singleton after its last screen/listener is gone.
        _activeRequests.value = emptyList()
    }

    private fun beginLocationUpdates() {
        locationManager.requestBackgroundUpdates(SharedLocationManager.REASON_NEED_HELP)
        locationJob?.cancel()
        locationJob = scope.launch {
            locationManager.location.collect { loc ->
                if (loc != null) updateLocation(loc)
            }
        }
    }

    private fun updateLocation(location: Location) {
        if (!_isSharing.value) return
        val uid = myUID
        if (uid.isEmpty()) return
        val last = lastSent
        val now = System.currentTimeMillis()
        if (last != null && now - lastSentMs < 5_000 && location.distanceTo(last) < 25f) {
            return
        }
        lastSent = Location(location)
        lastSentMs = now
        db.child("helpRequests").child(uid).updateChildren(
            mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "lastUpdated" to now / 1000.0
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "packride_help_session"
        private const val KEY_ACTIVE = "active"
        private const val KEY_OWNER_UID = "ownerUID"
        private const val KEY_TARGET_TYPE = "targetType"
        private const val KEY_TARGET_ID = "targetID"
        private const val KEY_TARGET_NAME = "targetName"
        private const val KEY_REQUESTER_NAME = "requesterName"
        private const val KEY_REQUESTER_INITIALS = "requesterInitials"
        private const val KEY_STARTED_AT_BITS = "startedAtBits"
        private const val KEY_LATITUDE_BITS = "latitudeBits"
        private const val KEY_LONGITUDE_BITS = "longitudeBits"

        fun shouldRestore(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)

        @Volatile
        private var instance: HelpRequestManager? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = HelpRequestManager(context.applicationContext)
                    }
                }
            }
        }

        fun get(): HelpRequestManager =
            instance ?: error("Call HelpRequestManager.init(Application) first")
    }
}
