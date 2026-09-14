package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 31, 2026 — bridges Home hero header's warning-triangle icon (see
 * HomeScreen.kt) directly to NeedHelpScreen instead of just landing on
 * SafetyHubScreen's default Crash sub-tab, matching iOS's HomeView, which
 * opens NeedHelpView directly as a fullScreenCover from the same icon (see
 * ContentView.swift's heroIconButton(system: "exclamationmark.triangle.fill")).
 *
 * Same bridge shape as nav/PendingMoreTab.kt one file over: HomeScreen sets
 * the target sub-tab index (0 = Crash, 1 = Need Help — see SafetyHubScreen's
 * own `when (tab)`); PackRideNav observes it to switch to the Safety tab;
 * SafetyHubScreen observes it to select that sub-tab and clears it once
 * consumed.
 */
object PendingSafetyTab {
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
