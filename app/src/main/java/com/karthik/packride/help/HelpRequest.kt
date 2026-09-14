package com.karthik.packride.help

data class HelpRequest(
    val id: String,
    val requesterUID: String,
    val requesterDeviceID: String,
    val requesterName: String,
    val requesterInitials: String,
    val latitude: Double,
    val longitude: Double,
    val startedAt: Double,
    val lastUpdated: Double,
    val targetType: String,
    val targetID: String,
    val targetName: String
) {
    /** Hidden if not updated in the last 20 minutes (iOS parity). */
    val isStale: Boolean
        get() = (System.currentTimeMillis() / 1000.0) - lastUpdated > 20 * 60
}

/**
 * Shared relevance filter — port of iOS ContentView's relevantHelpRequests.
 * Used by both NeedHelpScreen's own "Active help requests nearby" list and
 * the app-wide incoming-alert banner (PackRideNav), so a request only shows
 * up for the specific friend/community/group-ride it was actually shared
 * with, not every request in Firebase.
 */
fun List<HelpRequest>.relevantTo(
    myUID: String,
    myDeviceID: String,
    myCommunityIDs: Set<String>,
    activeRideCode: String
): List<HelpRequest> = filter { req ->
    if (req.requesterUID == myUID) return@filter false
    if (myDeviceID.isNotEmpty() && req.requesterDeviceID == myDeviceID) return@filter false
    // activeRequests is already the authenticated user's private helpAlerts
    // inbox. The server resolved friend/community/group membership before
    // writing it, so stale local membership caches must not hide a real SOS.
    true
}
