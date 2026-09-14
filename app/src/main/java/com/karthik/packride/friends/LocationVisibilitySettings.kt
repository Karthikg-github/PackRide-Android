package com.karthik.packride.friends

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocationVisibilityState(
    val shareWithFollowers: Boolean = false,
    val shareWithCommunities: Boolean = false,
    val nearbyRadiusMiles: Double = 1.0,
    val usesSelectedFollowers: Boolean = false,
    val selectedFollowerIds: Set<String> = emptySet(),
    val loaded: Boolean = false
)

/** Exact Firebase-schema counterpart of iOS LocationVisibilitySettings. */
class LocationVisibilitySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("packride_prefs", Context.MODE_PRIVATE)
    private val db = FirebaseDatabase.getInstance().reference
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val cachePrefix = "locationVisibility_${uid.ifBlank { "signedOut" }}_"

    private val _state = MutableStateFlow(
        LocationVisibilityState(
            shareWithFollowers = prefs.getBoolean(cachePrefix + "followers", false),
            shareWithCommunities = prefs.getBoolean(cachePrefix + "communities", false),
            nearbyRadiusMiles = prefs.getFloat(cachePrefix + "radiusMiles", 1f).toDouble(),
            usesSelectedFollowers = prefs.getBoolean(cachePrefix + "selectionEnabled", false),
            selectedFollowerIds = prefs.getStringSet(cachePrefix + "selectedFollowers", emptySet())?.toSet().orEmpty()
        )
    )
    val state: StateFlow<LocationVisibilityState> = _state.asStateFlow()

    fun load() {
        val userId = uid
        if (userId.isEmpty()) return
        db.child("users").child(userId).child("locationVisibility").get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    _state.value = _state.value.copy(loaded = true)
                    return@addOnSuccessListener
                }
                val selected = snapshot.child("selectedFollowers").children
                    .filter { it.getValue(Boolean::class.java) == true }
                    .mapNotNull { it.key }
                    .toSet()
                val next = LocationVisibilityState(
                    shareWithFollowers = snapshot.child("followers").getValue(Boolean::class.java) ?: false,
                    shareWithCommunities = snapshot.child("communities").getValue(Boolean::class.java) ?: false,
                    nearbyRadiusMiles = (snapshot.child("nearbyRadiusMiles").value as? Number)
                        ?.toDouble()
                        ?.takeIf { it.isFinite() }
                        ?.coerceIn(1.0, 50.0)
                        ?: _state.value.nearbyRadiusMiles.coerceIn(1.0, 50.0),
                    usesSelectedFollowers = snapshot.child("followerSelectionEnabled").getValue(Boolean::class.java) ?: false,
                    selectedFollowerIds = selected,
                    loaded = true
                )
                _state.value = next
                saveLocal(next)
            }
    }

    fun setShareWithFollowers(enabled: Boolean) = update(_state.value.copy(shareWithFollowers = enabled))
    fun setShareWithCommunities(enabled: Boolean) = update(_state.value.copy(shareWithCommunities = enabled))
    fun setNearbyRadiusMiles(miles: Double) = update(_state.value.copy(nearbyRadiusMiles = miles.coerceIn(1.0, 50.0)))

    fun useSelectedFollowers() = update(_state.value.copy(usesSelectedFollowers = true))

    fun shareWithAllFollowers() = update(
        _state.value.copy(usesSelectedFollowers = false, selectedFollowerIds = emptySet())
    )

    fun toggleFollower(id: String) {
        val selected = _state.value.selectedFollowerIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        update(_state.value.copy(usesSelectedFollowers = true, selectedFollowerIds = selected))
    }

    private fun update(next: LocationVisibilityState) {
        _state.value = next
        saveLocal(next)
        val userId = uid
        if (userId.isEmpty()) return
        db.child("users").child(userId).child("locationVisibility").setValue(
            mapOf(
                "followers" to next.shareWithFollowers,
                "communities" to next.shareWithCommunities,
                "nearbyRadiusMiles" to next.nearbyRadiusMiles,
                "followerSelectionEnabled" to next.usesSelectedFollowers,
                "selectedFollowers" to next.selectedFollowerIds.associateWith { true }
            )
        )
    }

    private fun saveLocal(value: LocationVisibilityState) {
        prefs.edit()
            .putBoolean(cachePrefix + "followers", value.shareWithFollowers)
            .putBoolean(cachePrefix + "communities", value.shareWithCommunities)
            .putFloat(cachePrefix + "radiusMiles", value.nearbyRadiusMiles.toFloat())
            .putBoolean(cachePrefix + "selectionEnabled", value.usesSelectedFollowers)
            .putStringSet(cachePrefix + "selectedFollowers", value.selectedFollowerIds)
            .apply()
    }
}
