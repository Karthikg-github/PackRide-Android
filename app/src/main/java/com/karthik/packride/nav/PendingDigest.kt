package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 31, 2026 — bridges Ride History's "Digest" quick-link (see
 * ui/screens/RideHistoryScreen.kt, port of iOS RideHistoryView.swift's
 * NavigationLink(destination: RidingDigestView())) to Dest.Digest, a real
 * top-level PackRideNav destination — matching iOS, which pushes
 * RidingDigestView from Ride History's own NavigationStack rather than
 * showing it as a tab. Same bridge shape as nav/PendingWaypoints.kt /
 * nav/PendingMotoRun.kt one file over: DigestScreen is a plain full-screen
 * push (not a bottom tab), so PackRideNav observes this and calls
 * navController.navigate(...) directly with no
 * popUpTo/launchSingleTop/restoreState.
 */
object PendingDigest {
    private val _pending = MutableStateFlow<Boolean?>(null)
    val pending: StateFlow<Boolean?> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    /** Call once consumed (navigated to) so it doesn't re-fire. */
    fun clear() {
        _pending.value = null
    }
}
