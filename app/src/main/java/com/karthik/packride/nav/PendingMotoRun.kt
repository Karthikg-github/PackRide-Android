package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 31, 2026 — bridges Home's "PACKRIDE" wordmark tap (the hidden Moto Run
 * easter egg — see HomeScreen.kt's heroCard, matching iOS ContentView.swift's
 * `.onTapGesture { showMotoRun = true }` on the same wordmark, ~line 387) to
 * Dest.MotoRun, a real top-level PackRideNav destination promoted out of
 * MoreScreen's flat tab strip (see PackRideNav.kt's Dest.MotoRun and the
 * removed "MotoRun" sub-tab in MoreScreen.kt). Same bridge shape as
 * nav/PendingWaypoints.kt one file over: MotoRunScreen is a plain full-screen
 * push (not a bottom tab), so PackRideNav observes this and calls
 * navController.navigate(...) directly with no
 * popUpTo/launchSingleTop/restoreState, matching iOS's fullScreenCover.
 */
object PendingMotoRun {
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
