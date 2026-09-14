package com.karthik.packride.analytics

import com.karthik.packride.ride.RideRecord

data class RideAnalyticsSummary(
    val totalRides: Int,
    val totalMiles: Double,
    val avgMiles: Double,
    val maxSpeedMph: Double,
    val totalDurationSeconds: Int,
    val groupRideCount: Int,
    val maxLean: Double
)

object RideAnalytics {
    fun summarize(rides: List<RideRecord>): RideAnalyticsSummary {
        if (rides.isEmpty()) {
            return RideAnalyticsSummary(0, 0.0, 0.0, 0.0, 0, 0, 0.0)
        }
        val miles = rides.sumOf { it.distanceMiles }
        return RideAnalyticsSummary(
            totalRides = rides.size,
            totalMiles = miles,
            avgMiles = miles / rides.size,
            maxSpeedMph = rides.maxOf { it.maxSpeedMph },
            totalDurationSeconds = rides.sumOf { it.durationSeconds },
            groupRideCount = rides.count { it.isGroupRide },
            maxLean = rides.maxOf { kotlin.math.abs(it.maxLeanAngle) }
        )
    }
}
