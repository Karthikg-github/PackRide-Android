package com.karthik.packride.group

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 30, 2026 — Ride History parity pass. MoreScreen's "Replay" is just
 * another tab in the same flat ScrollableTabRow Ride History lives in (no
 * navController, no way to pass a route argument), so tapping "View Route"
 * on a specific ride card requests that ride here, then switches MoreScreen
 * to the Replay tab — same cross-tab bridge pattern already used for
 * PendingGroupRide/PendingTurnByTurn. Before this, ReplayScreen always
 * showed whichever ride happened to be first in history, regardless of
 * which one you tapped.
 */
object PendingReplay {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(rideId: String) {
        _pending.value = rideId
    }

    fun consume() {
        _pending.value = null
    }
}
