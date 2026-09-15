package com.karthik.packride.feed

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.karthik.packride.storage.ImageCloudUpload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ride feed — port of iOS RideFeedManager (feedPosts RTDB).
 * Client pulls the latest public posts. Audience selection (Everyone versus
 * Following) is a presentation choice in FeedScreen; blocked riders are
 * always removed here so changing the selector cannot bypass moderation.
 */
class RideFeedManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference

    private val _posts = MutableStateFlow<List<FeedPost>>(emptyList())
    val posts: StateFlow<List<FeedPost>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _comments = MutableStateFlow<Map<String, List<FeedComment>>>(emptyMap())
    val comments: StateFlow<Map<String, List<FeedComment>>> = _comments.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var feedListener: ValueEventListener? = null
    private var feedQuery: Query? = null
    private val commentListeners = mutableMapOf<String, ValueEventListener>()

    val myID: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: ""

    /**
     * Aug 30, 2026 — port of iOS RideFeedManager.myKnownIDs. A post is authored
     * under whichever ID scheme was active when it was created (signed-in uid,
     * or this device's ANDROID_ID if posted while logged out) — that can differ
     * from whatever `myID` resolves to right now (e.g. signing in after posting
     * anonymously while logged out). Ownership checks (delete button, comment
     * moderation) compare against both possible IDs so a post/comment you made
     * doesn't stop being recognized as yours just because your auth state
     * changed since. Added for FeedScreen — the old screen only ever compared
     * against a single ID.
     */
    val myKnownIDs: Set<String>
        get() {
            val ids = mutableSetOf<String>()
            FirebaseAuth.getInstance().currentUser?.uid?.let { ids.add(it) }
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)?.let { ids.add(it) }
            return ids
        }

    fun listenForFeed(blockedIDs: Set<String> = emptySet()) {
        stopListening()
        if (myID.isEmpty()) return
        _isLoading.value = true
        val query = db.child("feedPosts").limitToLast(100)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<FeedPost>()
                for (child in snapshot.children) {
                    val post = parsePost(child) ?: continue
                    if (post.authorID !in blockedIDs) loaded.add(post)
                }
                loaded.sortByDescending { it.timestamp }
                _posts.value = loaded
                _isLoading.value = false
            }
            override fun onCancelled(error: DatabaseError) {
                _isLoading.value = false
                _error.value = error.message
            }
        }
        query.addValueEventListener(l)
        feedQuery = query
        feedListener = l
    }

    fun stopListening() {
        feedListener?.let { listener -> feedQuery?.removeEventListener(listener) }
        feedListener = null
        feedQuery = null
        commentListeners.forEach { (postId, l) ->
            db.child("feedPosts").child(postId).child("comments").removeEventListener(l)
        }
        commentListeners.clear()
    }

    /**
     * Aug 30, 2026 — photoUri support added. When present, the image is
     * uploaded to Firebase Storage (see ImageCloudUpload) BEFORE the post
     * metadata is written, so photoURL can be included in the same
     * feedPosts/{postID} write iOS does. If the photo upload fails, the
     * post is still written without a photo (mirrors iOS's RideFeedManager
     * comment: a post can have a route, a photo, both, or neither — never
     * block the whole post on the photo) and `onDone` reports success=true
     * with a warning message so the caller can tell the rider what happened
     * without leaving the UI stuck on a spinner.
     */
    fun postRide(
        context: Context,
        title: String,
        distanceMiles: Double,
        duration: String,
        authorName: String,
        authorInitials: String,
        route: List<FeedRoutePoint> = emptyList(),
        maxSpeedMph: Double = 0.0,
        rideScore: Int? = null,
        turnCount: Int? = null,
        isAnonymous: Boolean = false,
        photoUri: Uri? = null,
        onDone: (success: Boolean, message: String?) -> Unit
    ) {
        if (myID.isEmpty()) {
            onDone(false, "Not signed in — try logging out and back in.")
            return
        }
        val postID = db.child("feedPosts").push().key
        if (postID == null) {
            onDone(false, "Could not allocate post id")
            return
        }
        val data = mutableMapOf<String, Any>(
            "authorID" to myID,
            "authorName" to if (isAnonymous) "Anonymous Rider" else authorName,
            "authorInitials" to if (isAnonymous) "?" else authorInitials,
            "timestamp" to System.currentTimeMillis() / 1000.0,
            "title" to title.trim(),
            "distance" to distanceMiles,
            "duration" to duration
        )
        if (isAnonymous) data["isAnonymous"] = true
        if (route.isNotEmpty()) {
            data["route"] = route.map { mapOf("lat" to it.lat, "lng" to it.lng) }
        }
        if (maxSpeedMph > 0) data["maxSpeedMph"] = maxSpeedMph
        rideScore?.let { data["rideScore"] = it }
        turnCount?.let { data["turnCount"] = it }

        fun writePost(warning: String?) {
            db.child("feedPosts").child(postID).setValue(data)
                .addOnSuccessListener { onDone(true, warning) }
                .addOnFailureListener { e -> onDone(false, e.localizedMessage) }
        }

        if (photoUri != null) {
            ImageCloudUpload.uploadFeedPhoto(context, postID, photoUri) { result ->
                result.onSuccess { url ->
                    data["photoURL"] = url
                    writePost(null)
                }.onFailure { e ->
                    writePost("Posted, but the photo failed to upload: ${e.localizedMessage ?: "unknown error"}")
                }
            }
        } else {
            writePost(null)
        }
    }

    fun postLapSession(
        title: String,
        trackName: String,
        laps: List<Double>,
        bestLapTime: Double,
        distanceMiles: Double,
        authorName: String,
        authorInitials: String,
        onDone: (String?) -> Unit
    ) {
        if (myID.isEmpty()) {
            onDone("Not signed in — try logging out and back in.")
            return
        }
        val postID = db.child("feedPosts").push().key ?: return onDone("Could not allocate post id")
        val data = mapOf(
            "authorID" to myID,
            "authorName" to authorName,
            "authorInitials" to authorInitials,
            "timestamp" to System.currentTimeMillis() / 1000.0,
            "title" to title.trim(),
            "distance" to distanceMiles,
            "duration" to "",
            "isLapSession" to true,
            "trackName" to trackName,
            "bestLapTime" to bestLapTime,
            "lapTimes" to laps
        )
        db.child("feedPosts").child(postID).setValue(data)
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { e -> onDone(e.localizedMessage) }
    }

    fun deletePost(postID: String, onDone: (String?) -> Unit = {}) {
        if (myID.isEmpty()) {
            onDone("Not signed in")
            return
        }
        db.child("feedPosts").child(postID).removeValue()
            .addOnSuccessListener {
                com.google.firebase.storage.FirebaseStorage.getInstance().reference
                    .child("feedPhotos/$postID.jpg").delete()
                onDone(null)
            }
            .addOnFailureListener { e -> onDone(e.localizedMessage) }
    }

    fun setReaction(postID: String, emoji: String?) {
        if (myID.isEmpty()) return
        val ref = db.child("feedPosts").child(postID).child("reactions").child(myID)
        if (emoji != null) ref.setValue(emoji) else ref.removeValue()
    }

    fun toggleBookmark(postID: String, currentlyBookmarked: Boolean) {
        if (myID.isEmpty()) return
        val ref = db.child("feedPosts").child(postID).child("bookmarks").child(myID)
        if (currentlyBookmarked) ref.removeValue() else ref.setValue(true)
    }

    fun listenForComments(postID: String) {
        commentListeners.remove(postID)?.let {
            db.child("feedPosts").child(postID).child("comments").removeEventListener(it)
        }
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<FeedComment>()
                for (child in snapshot.children) {
                    val data = child.value as? Map<*, *> ?: continue
                    list.add(
                        FeedComment(
                            id = child.key ?: continue,
                            userID = data["userID"] as? String ?: continue,
                            userName = data["userName"] as? String ?: "Rider",
                            text = data["text"] as? String ?: continue,
                            timestamp = (data["timestamp"] as? Number)?.toDouble() ?: 0.0
                        )
                    )
                }
                list.sortBy { it.timestamp }
                _comments.value = _comments.value + (postID to list)
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        db.child("feedPosts").child(postID).child("comments").addValueEventListener(l)
        commentListeners[postID] = l
    }

    fun addComment(postID: String, userName: String, text: String) {
        if (myID.isEmpty() || text.isBlank()) return
        val data = mapOf(
            "userID" to myID,
            "userName" to userName,
            "text" to text.trim(),
            "timestamp" to System.currentTimeMillis() / 1000.0
        )
        db.child("feedPosts").child(postID).child("comments").push().setValue(data)
    }

    /**
     * Aug 30, 2026 — added for FeedScreen's comments sheet (port of iOS
     * deleteComment). Either the comment's own author OR the post's author can
     * remove a comment, same moderation model as iOS — the Firebase rules need
     * to grant both, not just an exact match on the comment's userID.
     */
    fun deleteComment(postID: String, commentID: String, onDone: (String?) -> Unit = {}) {
        if (myID.isEmpty()) {
            onDone("Not signed in — try logging out and back in.")
            return
        }
        db.child("feedPosts").child(postID).child("comments").child(commentID).removeValue()
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { e -> onDone(e.localizedMessage) }
    }

    private fun parsePost(child: DataSnapshot): FeedPost? {
        val data = child.value as? Map<*, *> ?: return null
        val id = child.key ?: return null
        val authorID = data["authorID"] as? String ?: return null
        val authorName = data["authorName"] as? String ?: return null
        val reactions = data["reactions"] as? Map<*, *>
        val reactionCount = reactions?.size ?: (data["likes"] as? Map<*, *>)?.size ?: 0
        val myReaction = reactions?.get(myID) as? String
            ?: if ((data["likes"] as? Map<*, *>)?.containsKey(myID) == true) "🔥" else null
        val bookmarks = data["bookmarks"] as? Map<*, *>
        val comments = data["comments"] as? Map<*, *>
        val routeRaw = data["route"] as? List<*>
        val route = routeRaw?.mapNotNull { pt ->
            val m = pt as? Map<*, *> ?: return@mapNotNull null
            val lat = (m["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
            val lng = (m["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
            FeedRoutePoint(lat, lng)
        } ?: emptyList()
        val lapTimes = (data["lapTimes"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
        return FeedPost(
            id = id,
            authorID = authorID,
            authorName = authorName,
            authorInitials = data["authorInitials"] as? String ?: "?",
            timestamp = (data["timestamp"] as? Number)?.toDouble() ?: 0.0,
            title = data["title"] as? String ?: "",
            distanceMiles = (data["distance"] as? Number)?.toDouble() ?: 0.0,
            duration = data["duration"] as? String ?: "",
            route = route,
            reactionCount = reactionCount,
            myReaction = myReaction,
            commentCount = comments?.size ?: 0,
            photoURL = data["photoURL"] as? String,
            bookmarkedByMe = bookmarks?.containsKey(myID) == true,
            isLapSession = data["isLapSession"] as? Boolean ?: false,
            trackName = data["trackName"] as? String ?: "",
            lapTimes = lapTimes,
            bestLapTime = (data["bestLapTime"] as? Number)?.toDouble() ?: 0.0,
            isAnonymous = data["isAnonymous"] as? Boolean ?: false,
            maxSpeedMph = (data["maxSpeedMph"] as? Number)?.toDouble() ?: 0.0,
            rideScore = (data["rideScore"] as? Number)?.toInt(),
            turnCount = (data["turnCount"] as? Number)?.toInt()
        )
    }
}
