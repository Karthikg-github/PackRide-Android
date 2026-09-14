package com.karthik.packride.profile

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class SharedProfileImages(val avatarUrl: String = "", val bannerUrl: String = "")
data class SharedProfileImageBytes(val avatar: ByteArray? = null, val banner: ByteArray? = null)

/** Resolves iOS/Android profile URLs from RTDB, then the shared Storage paths as a repair fallback. */
object ProfileImageResolver {
    suspend fun fetchBytes(uid: String): SharedProfileImageBytes = coroutineScope {
        val storage = FirebaseStorage.getInstance().reference.child("users").child(uid)
        val avatar = async { runCatching { storage.child("avatar.jpg").getBytes(15L * 1024 * 1024).await() }.getOrNull() }
        val banner = async { runCatching { storage.child("banner.jpg").getBytes(20L * 1024 * 1024).await() }.getOrNull() }
        SharedProfileImageBytes(avatar.await(), banner.await())
    }

    suspend fun resolve(uid: String): SharedProfileImages = coroutineScope {
        val root = FirebaseDatabase.getInstance().reference
        val privateDeferred = async { runCatching { root.child("users").child(uid).child("profile").get().await().value as? Map<*, *> }.getOrNull().orEmpty() }
        val publicDeferred = async { runCatching { root.child("publicRiders").child(uid).get().await().value as? Map<*, *> }.getOrNull().orEmpty() }
        val privateData = privateDeferred.await()
        val publicData = publicDeferred.await()
        fun value(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            (privateData[key] as? String)?.takeIf(String::isNotBlank)
                ?: (publicData[key] as? String)?.takeIf(String::isNotBlank)
        }.orEmpty()
        var avatar = value("avatarURL", "avatarUrl", "profileImageURL", "photoURL")
        var banner = value("bannerURL", "bannerUrl", "heroImageURL", "coverPhotoURL")
        val storage = FirebaseStorage.getInstance().reference.child("users").child(uid)
        if (avatar.isBlank()) avatar = runCatching { storage.child("avatar.jpg").downloadUrl.await().toString() }.getOrDefault("")
        if (banner.isBlank()) banner = runCatching { storage.child("banner.jpg").downloadUrl.await().toString() }.getOrDefault("")
        if (avatar.isNotBlank() || banner.isNotBlank()) {
            val updates = mutableMapOf<String, Any>()
            if (avatar.isNotBlank()) { updates["users/$uid/profile/avatarURL"] = avatar; updates["publicRiders/$uid/avatarURL"] = avatar }
            if (banner.isNotBlank()) { updates["users/$uid/profile/bannerURL"] = banner; updates["publicRiders/$uid/bannerURL"] = banner }
            runCatching { root.updateChildren(updates).await() }
        }
        SharedProfileImages(avatar, banner)
    }
}
