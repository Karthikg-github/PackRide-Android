package com.karthik.packride.auth

import android.content.Context
import android.app.Activity
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.karthik.packride.data.UserPrefs
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.help.HelpRequestManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firebase email/password auth — Kotlin port of iOS AuthManager.
 * Hydrates local profile from RTDB on reinstall / new-device login.
 */
class AuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val prefs = UserPrefs(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(prefs.hasCompletedOnboarding)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    val uid: String? get() = auth.currentUser?.uid
    val prefsSnapshot: UserPrefs get() = prefs

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                scope.launch {
                    hydrateLocalProfileIfNeeded(user.uid)
                    writeDeviceId(user.uid)
                    syncCachedPushToken(user.uid)
                    CommunityMembershipStore.get().syncCurrentIdentity()
                    _isLoggedIn.value = true
                    _isLoading.value = false
                    _hasCompletedOnboarding.value = prefs.hasCompletedOnboarding
                }
            } else {
                _isLoggedIn.value = false
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = ""
    }

    fun login(email: String, password: String) {
        _errorMessage.value = ""
        scope.launch {
            try {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            }
        }
    }

    fun register(email: String, password: String) {
        _errorMessage.value = ""
        scope.launch {
            try {
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            }
        }
    }

    fun signInWithGoogleToken(idToken: String) = signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))

    fun signInWithFacebookToken(accessToken: String) = signInWithCredential(FacebookAuthProvider.getCredential(accessToken))

    fun signInWithApple(activity: Activity) {
        _errorMessage.value = ""
        val provider = OAuthProvider.newBuilder("apple.com").apply {
            scopes = listOf("email", "name")
        }
        auth.pendingAuthResult?.addOnFailureListener { _errorMessage.value = friendlyError(it as Exception) }
            ?: auth.startActivityForSignInWithProvider(activity, provider.build())
                .addOnFailureListener { _errorMessage.value = friendlyError(it as Exception) }
    }

    fun configurationError(provider: String) {
        _errorMessage.value = "$provider sign-in needs its Android app credentials added before it can be used."
    }

    private fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential) {
        _errorMessage.value = ""
        scope.launch {
            try { auth.signInWithCredential(credential).await() }
            catch (e: Exception) { _errorMessage.value = friendlyError(e) }
        }
    }

    fun resetPassword(email: String, onResult: (Boolean) -> Unit) {
        _errorMessage.value = ""
        scope.launch {
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                onResult(true)
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
                onResult(false)
            }
        }
    }

    fun signOut() {
        // Never carry an account-targeted emergency share into the next
        // signed-in account on a shared device.
        HelpRequestManager.get().stop()
        auth.signOut()
    }

    fun completeOnboarding(name: String, bike: String, city: String, experience: String) {
        prefs.riderName = name
        prefs.riderBike = bike
        prefs.riderCity = city
        prefs.riderExperience = experience
        prefs.hasCompletedOnboarding = true
        _hasCompletedOnboarding.value = true

        val id = uid ?: return
        val profile = mapOf(
            "name" to name,
            "initials" to name.rideInitials(),
            "bike" to bike,
            "city" to city,
            "experience" to experience,
            "isOnline" to true,
            "lastSeen" to System.currentTimeMillis() / 1000.0
        )
        db.updateChildren(
            mapOf(
                "users/$id/profile" to profile,
                "publicRiders/$id" to profile.filterKeys { it != "isOnline" && it != "lastSeen" }
            )
        )
    }

    fun deleteAccount(onResult: (String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult("No signed-in account found.")
            return
        }
        val id = user.uid
        HelpRequestManager.get().stop()
        scope.launch {
            try {
                val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
                if (lastSignIn == 0L || System.currentTimeMillis() - lastSignIn > 5 * 60_000L) {
                    onResult("For your security, sign out and sign back in before deleting your account.")
                    return@launch
                }

                val userSnapshot = db.child("users").child(id).get().await()
                val following = userSnapshot.child("following").children.mapNotNull { it.key }
                val followers = userSnapshot.child("followers").children.mapNotNull { it.key }
                val authoredPosts = db.child("feedPosts").orderByChild("authorID").equalTo(id).get().await()
                val allPosts = db.child("feedPosts").get().await()
                val publicRiders = db.child("publicRiders").get().await()
                val communities = db.child("communities").get().await()
                val scheduledRides = db.child("scheduledRides").get().await()

                val removals = mutableMapOf<String, Any?>()
                removals["users/$id"] = null
                removals["publicRiders/$id"] = null
                removals["helpRequests/$id"] = null
                following.forEach { target -> removals["users/$target/followers/$id"] = null }
                followers.forEach { follower -> removals["users/$follower/following/$id"] = null }
                publicRiders.children.mapNotNull { it.key }.forEach { target ->
                    removals["users/$target/followRequests/$id"] = null
                }

                val authoredPostIds = authoredPosts.children.mapNotNull { it.key }.toSet()
                authoredPostIds.forEach { removals["feedPosts/$it"] = null }
                allPosts.children.forEach { post ->
                    val postId = post.key ?: return@forEach
                    if (postId in authoredPostIds) return@forEach
                    removals["feedPosts/$postId/reactions/$id"] = null
                    removals["feedPosts/$postId/likes/$id"] = null
                    removals["feedPosts/$postId/bookmarks/$id"] = null
                    post.child("comments").children.forEach { comment ->
                        if (comment.child("userID").getValue(String::class.java) == id) {
                            comment.key?.let { removals["feedPosts/$postId/comments/$it"] = null }
                        }
                    }
                }

                communities.children.forEach { community ->
                    val communityId = community.key ?: return@forEach
                    community.child("members").children.forEach { member ->
                        if (member.child("authUID").getValue(String::class.java) == id) {
                            member.key?.let { removals["communities/$communityId/members/$it"] = null }
                        }
                    }
                }
                scheduledRides.children.forEach { ride ->
                    val rideId = ride.key ?: return@forEach
                    if (ride.child("creatorID").getValue(String::class.java) == id) {
                        removals["scheduledRides/$rideId"] = null
                    } else {
                        removals["scheduledRides/$rideId/rsvps/$id"] = null
                    }
                }

                // Storage rules intentionally do not permit listing the whole
                // users/{uid} root. Delete the known private objects and the
                // replay collection explicitly; a denied/missing object must
                // never prevent deletion of the account itself.
                val storageRoot = FirebaseStorage.getInstance().reference
                runCatching { storageRoot.child("users/$id/avatar.jpg").delete().await() }
                runCatching { storageRoot.child("users/$id/banner.jpg").delete().await() }
                runCatching {
                    storageRoot.child("users/$id/rides").listAll().await().items.forEach { item ->
                        runCatching { item.delete().await() }
                    }
                }
                authoredPostIds.forEach { postId ->
                    runCatching { FirebaseStorage.getInstance().reference.child("feedPhotos/$postId.jpg").delete().await() }
                }
                db.updateChildren(removals).await()
                user.delete().await()
                clearLocalAccountData()
                onResult(null)
            } catch (e: Exception) {
                onResult(friendlyError(e))
            }
        }
    }

    private fun clearLocalAccountData() {
        listOf(
            "packride_prefs", "packride_rides", "packride_waypoints", "packride_communities",
            "packride_garage", "packride_solo_session", "packride_activeride", "packride_help_session",
            "packride_track_locations"
        ).forEach { appContext.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply() }
        appContext.filesDir.resolve("rides").listFiles()?.forEach { it.delete() }
    }

    private suspend fun hydrateLocalProfileIfNeeded(uid: String) {
        withTimeoutOrNull(6_000) {
            try {
                val snap = db.child("users").child(uid).child("profile").get().await()
                @Suppress("UNCHECKED_CAST")
                val privateData = snap.value as? Map<String, Any> ?: emptyMap()
                val publicSnap = db.child("publicRiders").child(uid).get().await()
                @Suppress("UNCHECKED_CAST")
                val publicData = publicSnap.value as? Map<String, Any> ?: emptyMap()
                val data = publicData + privateData
                val name = (data["name"] as? String)?.takeIf { it.isNotBlank() } ?: prefs.riderName
                if (data.isEmpty()) return@withTimeoutOrNull
                prefs.applyServerProfile(
                    name = name,
                    bike = data["bike"] as? String,
                    city = data["city"] as? String,
                    experience = data["experience"] as? String,
                    avatar = data["avatarURL"] as? String,
                    banner = data["bannerURL"] as? String
                )
            } catch (_: Exception) {
                // Offline / timeout → onboarding as new rider
            }
        }
    }

    private fun writeDeviceId(uid: String) {
        val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: return
        db.child("users").child(uid).child("deviceID").setValue(deviceId)
    }

    private fun syncCachedPushToken(uid: String) {
        val token = appContext.getSharedPreferences("packride_prefs", Context.MODE_PRIVATE)
            .getString("fcmToken", "")
            .orEmpty()
        if (token.isNotEmpty()) {
            val userRef = db.child("users").child(uid)
            val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            userRef.child("fcmToken").setValue(token)
            if (!deviceId.isNullOrBlank()) userRef.child("fcmTokens").child("android-$deviceId").setValue(token)
        }
    }

    private fun friendlyError(error: Exception): String {
        val code = (error as? FirebaseAuthException)?.errorCode
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "That doesn't look like a valid email address."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
                "Incorrect password. Try again or reset it."
            "ERROR_USER_NOT_FOUND" -> "No account found with that email."
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "An account with that email already exists. Try logging in."
            "ERROR_WEAK_PASSWORD" -> "Password must be at least 6 characters."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "Network error. Check your connection and try again."
            "ERROR_REQUIRES_RECENT_LOGIN" ->
                "For security, please sign out and sign back in, then try again."
            else -> error.localizedMessage ?: "Something went wrong."
        }
    }
}

fun String.rideInitials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}
