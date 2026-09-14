package com.karthik.packride.group

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 30, 2026 — bridges "Plan Route → Group Ride" (WaypointsScreen, nested
 * deep inside the More tab) to the Group tab (a real top-level PackRideNav
 * route) without a navController reference threaded all the way down.
 *
 * Kotlin equivalent of iOS's DeepLinkRouter.pendingRideCode, added for the
 * same reason: the old approach (WaypointsScreen constructing/launching a
 * GroupRideSession or a screen directly) would either present outside
 * PackRideNav's tab structure (losing the bottom bar, the exact iOS bug
 * fixed earlier this session) or duplicate GroupRideScreen's own session
 * lifecycle. Instead: WaypointsScreen sets pendingCode + pendingIsLeader and
 * requests the Group tab; PackRideNav observes it and switches tabs;
 * GroupRideScreen consumes it once on first composition to auto-join/create
 * with the already-published route already sitting at
 * rides/{code}/waypointsJSON (see WaypointsManager.bindToNewRideCode +
 * saveWaypoints/GroupWaypointSync.publish).
 */
object PendingGroupRide {
    data class Pending(val code: String, val isLeader: Boolean)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun request(code: String, isLeader: Boolean) {
        _pending.value = Pending(code, isLeader)
    }

    /** Call once consumed (tab switched / session started) so it doesn't re-fire. */
    fun clear() {
        _pending.value = null
    }
}
