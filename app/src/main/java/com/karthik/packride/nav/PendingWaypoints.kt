package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 31, 2026 — bridges Home's "Plan Route" row to Dest.Waypoints, a real
 * top-level PackRideNav destination promoted out of MoreScreen's flat tab
 * strip (see PackRideNav.kt's Dest.Waypoints and the removed "Waypoints"
 * sub-tab in MoreScreen.kt). Same bridge shape as nav/PendingTurnByTurn.kt
 * one file over: WaypointsScreen is a plain full-screen push (not a bottom
 * tab), so PackRideNav observes this and calls navController.navigate(...)
 * directly with no popUpTo/launchSingleTop/restoreState, matching how
 * pendingTurnByTurn is handled.
 */
object PendingWaypoints {
    data class Request(val rideCode: String = "", val isLeader: Boolean = false)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    fun request(rideCode: String = "", isLeader: Boolean = false) {
        _pending.value = Request(rideCode.uppercase(), isLeader)
    }

    /** Call once consumed (navigated to) so it doesn't re-fire. */
    fun clear() {
        _pending.value = null
    }
}
