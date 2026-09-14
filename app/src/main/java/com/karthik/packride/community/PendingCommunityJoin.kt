package com.karthik.packride.community

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 30, 2026 — bridges a tapped `packride://joincommunity?id=XXXX&passcode=YYYY`
 * link to My Communities without a navController reference threaded down into
 * MainActivity. Kotlin equivalent of iOS's DeepLinkRouter.pendingCommunityJoin
 * (see PackRideApp.swift), and same shape/purpose as PendingGroupRide.kt one
 * package over — MainActivity.handleDeepLink() populates this from the
 * incoming Intent's data Uri; PackRideNav observes it to switch to the More
 * tab, MoreScreen observes it to select the Community sub-tab, and
 * CommunityScreen consumes it once on appear to prefill (not auto-submit)
 * Join Community — see CommunityScreen.kt's CommunityScreen composable.
 *
 * A community join carries two values (id + passcode), unlike a group ride's
 * single code, so it gets its own small struct rather than reusing
 * PendingGroupRide's single-String shape — same reason iOS's
 * PendingCommunityJoin struct exists separately from pendingRideCode.
 */
object PendingCommunityJoin {
    data class Pending(val id: String, val passcode: String)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun request(id: String, passcode: String) {
        _pending.value = Pending(id, passcode)
    }

    /** Call once consumed (prefilled into Join Community) so it doesn't re-fire. */
    fun clear() {
        _pending.value = null
    }
}
