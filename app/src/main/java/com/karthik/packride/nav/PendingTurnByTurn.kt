package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 30, 2026 — same bridge pattern as group/PendingGroupRide.kt: WaypointsScreen
 * (and InAppNavScreen) are nested inside the More/Nav tabs, not top-level
 * PackRideNav routes, so "Navigate" can't hold a navController reference to
 * push a new destination directly. Instead the requester sets a pending
 * target here; PackRideNav observes it and navigates to Dest.TurnByTurn
 * (hiding the bottom bar while there, matching iOS's fullScreenCover);
 * TurnByTurnScreen consumes it once on first composition.
 */
object PendingTurnByTurn {
    data class Pending(
        val destLat: Double,
        val destLng: Double,
        val destName: String,
        // Intermediate stops between current location and the destination,
        // in route order — mirrors iOS TurnByTurnView(destination:, waypoints:).
        val waypoints: List<Pair<Double, Double>> = emptyList()
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun request(destLat: Double, destLng: Double, destName: String, waypoints: List<Pair<Double, Double>> = emptyList()) {
        _pending.value = Pending(destLat, destLng, destName, waypoints)
    }

    /** Call once consumed (screen navigated to / started) so it doesn't re-fire. */
    fun clear() {
        _pending.value = null
    }
}
