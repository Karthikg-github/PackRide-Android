package com.karthik.packride.feed

data class FeedRoutePoint(val lat: Double, val lng: Double)

data class FeedComment(
    val id: String,
    val userID: String,
    val userName: String,
    val text: String,
    val timestamp: Double
)

data class FeedPost(
    val id: String,
    val authorID: String,
    val authorName: String,
    val authorInitials: String,
    val timestamp: Double,
    val title: String,
    val distanceMiles: Double,
    val duration: String,
    val route: List<FeedRoutePoint> = emptyList(),
    val reactionCount: Int = 0,
    val myReaction: String? = null,
    val commentCount: Int = 0,
    val photoURL: String? = null,
    val bookmarkedByMe: Boolean = false,
    val isLapSession: Boolean = false,
    val trackName: String = "",
    val lapTimes: List<Double> = emptyList(),
    val bestLapTime: Double = 0.0,
    val isAnonymous: Boolean = false,
    val maxSpeedMph: Double = 0.0,
    val rideScore: Int? = null,
    val turnCount: Int? = null
) {
    val distanceString: String get() = com.karthik.packride.data.MeasurementUnits.distanceMiles(distanceMiles)
}
