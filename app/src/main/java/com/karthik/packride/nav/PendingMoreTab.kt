package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 31, 2026 — bridges Home's flat feature-row list (Plan Route, Track
 * Mode, Friends Nearby, Communities, Ride History — see HomeScreen.kt, part
 * of the Home-screen parity pass replacing the old 4-tile grid with iOS's
 * real Ride/The Pack/You sectioned list) to whichever sub-tab each one
 * actually lives at inside MoreScreen's own ScrollableTabRow, without a
 * navController reference threaded down into HomeScreen.
 *
 * Same shape/purpose as group/PendingGroupRide.kt, community/PendingCommunityJoin.kt
 * and nav/PendingTurnByTurn.kt one directory over: HomeScreen's row taps
 * populate this with the target sub-tab index; PackRideNav observes it to
 * switch to the More tab; MoreScreen observes it to select that sub-tab and
 * clears it once consumed.
 */
object PendingMoreTab {
    private val _pending = MutableStateFlow<Int?>(null)
    val pending: StateFlow<Int?> = _pending.asStateFlow()

    fun request(tabIndex: Int) {
        _pending.value = tabIndex
    }

    /** Call once consumed (sub-tab selected) so it doesn't re-fire. */
    fun clear() {
        _pending.value = null
    }
}
