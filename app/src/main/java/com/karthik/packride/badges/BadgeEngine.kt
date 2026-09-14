package com.karthik.packride.badges

import com.karthik.packride.analytics.RideAnalyticsEngine
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.ride.RideRecord
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class BadgeCategory(val displayName: String) {
    ROAD("Road"),
    TRACK("Track"),
    STREAKS("Streaks")
}

// Drives how a badge's progress readout is formatted ("320 / 500 mi" vs
// "82 / 95" vs "3 / 7 days" etc) — iOS BadgesView.BadgeValueKind parity.
enum class BadgeValueKind { MILES, SCORE, DEGREES, COUNT, DAYS }

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val category: BadgeCategory,
    val kind: BadgeValueKind,
    // Both already clamped to target by BadgeEngine below — an earned badge
    // shows a filled/gold treatment instead of a readout, so there's nothing
    // to gain by letting current run past target here.
    val current: Double,
    val target: Double
) {
    val earned: Boolean get() = current >= target
    val progress: Float get() = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f

    val readout: String
        get() {
            val cur = numberString(current)
            val tgt = numberString(target)
            return when (kind) {
                BadgeValueKind.MILES -> "$cur / $tgt mi"
                BadgeValueKind.DEGREES -> "$cur / $tgt°"
                BadgeValueKind.DAYS -> "$cur / $tgt days"
                BadgeValueKind.SCORE, BadgeValueKind.COUNT -> "$cur / $tgt"
            }
        }

    private companion object {
        // Miles/degrees read fine as whole numbers for badge purposes; score/
        // count/day targets are always whole numbers already.
        fun numberString(value: Double): String = value.roundToInt().toString()
    }
}

/**
 * Pure computation over ride history — iOS BadgesView.swift / BadgeEngine
 * parity (see RideAnalyticsEngine.swift's StreakEngine + LapAnalyticsEngine
 * for the source logic ported below). A badge is simply "earned" whenever
 * the rider's current stats already clear its target, recomputed fresh every
 * time the screen collects `rides`.
 *
 * Android has no separate LapHistoryManager/LapRecord — a track session is
 * just a RideRecord with lapTimes.isNotEmpty() (RideRecord.isTrackSession),
 * so every badge below is computed from the single `rides` list, splitting
 * road vs. track by that flag where iOS splits by manager.
 *
 * Aug 31, 2026 — "Smooth Operator" (Ride Score 95+) restored, now that
 * analytics/RideAnalyticsEngine.kt exists (Kotlin port of iOS's
 * RideAnalyticsEngine.swift, GPX speed/g-force/lean analysis). Unlike every
 * other badge here, Ride Score needs real I/O (parsing a ride's GPX file),
 * so it does NOT go through the plain synchronous [compute] below — see
 * [computeAsync]. iOS caches its analytics summary on RideRecord/LapRecord
 * once, right when a ride ends, and BadgesView just reads that cached
 * field; Android's RideRecord has no such cache (would need to thread a new
 * field through RideRecord's JSON (de)serialization AND
 * RideHistoryManager's Firebase sync map, out of scope here), so
 * [computeAsync] instead scans every road ride's *local* GPX file fresh on
 * each call, off the main thread. That trades a cached O(1) read for real
 * I/O on every call, but it works retroactively on a rider's whole existing
 * history instead of only rides recorded after this shipped, and GPX
 * parsing (regex over a local text file, same approach RideAnalyticsEngine
 * already uses) is cheap enough that scanning a full ride history in the
 * background is not a real-world problem for this app's scale.
 * "Consistency King" needs no such change: unlike Ride Score, iOS's
 * consistency score is computed purely from a session's lap-time spread (no
 * GPX needed), so it stays cheap and synchronous below.
 */
object BadgeEngine {
    fun compute(rides: List<RideRecord>, bestRideScore: Int = 0): List<Badge> {
        val roadRides = rides.filterNot { it.isTrackSession }
        val trackSessions = rides.filter { it.isTrackSession }

        // MARK: Road aggregates
        val totalRoadMiles = roadRides.sumOf { it.distanceMiles }
        val totalRoadRides = roadRides.size
        val bestSingleRideMiles = roadRides.maxOfOrNull { it.distanceMiles } ?: 0.0
        val hasNightRide = roadRides.any { isNightRide(it.dateMs) }

        // MARK: Track aggregates
        val totalSessions = trackSessions.size
        // Needs at least 2 completed laps to have a spread to measure,
        // same guard as iOS's LapAnalyticsEngine.analyze.
        val bestConsistency = trackSessions
            .filter { it.lapTimes.size >= 2 }
            .maxOfOrNull { consistencyScore(it.lapTimes) } ?: 0

        // Max lean is a single field on RideRecord regardless of road/track
        // in Android's unified model (iOS keeps it on two different types).
        val bestLean = rides.maxOfOrNull { it.maxLeanAngle } ?: 0.0

        // MARK: Streaks (full history, road + track combined, not scoped to any window)
        val activeDays = activeDays(rides)
        val longestStreak = longestStreak(activeDays)

        return listOf(
            // MARK: Road
            Badge(
                id = "first_ride", name = "First Ride", description = "Complete your first road ride.",
                category = BadgeCategory.ROAD, kind = BadgeValueKind.COUNT,
                current = minOf(totalRoadRides.toDouble(), 1.0), target = 1.0
            ),
            Badge(
                id = "century_rider", name = "Century Rider", description = "Ride 100+ miles in a single ride.",
                category = BadgeCategory.ROAD, kind = BadgeValueKind.MILES,
                current = minOf(bestSingleRideMiles, 100.0), target = 100.0
            ),
            Badge(
                id = "500_mile_club", name = "500 Mile Club", description = "Rack up 500 cumulative road miles.",
                category = BadgeCategory.ROAD, kind = BadgeValueKind.MILES,
                current = minOf(totalRoadMiles, 500.0), target = 500.0
            ),
            Badge(
                id = "1000_mile_club", name = "1,000 Mile Club", description = "Rack up 1,000 cumulative road miles.",
                category = BadgeCategory.ROAD, kind = BadgeValueKind.MILES,
                current = minOf(totalRoadMiles, 1000.0), target = 1000.0
            ),
            Badge(
                id = "smooth_operator", name = "Smooth Operator", description = "Score 95+ Ride Score on any single ride.",
                category = BadgeCategory.ROAD, kind = BadgeValueKind.SCORE,
                current = minOf(bestRideScore.toDouble(), 95.0), target = 95.0
            ),
            Badge(
                id = "night_rider", name = "Night Rider", description = "Complete a ride that starts between 9pm and 5am.",
                category = BadgeCategory.ROAD, kind = BadgeValueKind.COUNT,
                current = if (hasNightRide) 1.0 else 0.0, target = 1.0
            ),

            // MARK: Track
            Badge(
                id = "first_lap", name = "First Lap", description = "Complete your first Track Mode session.",
                category = BadgeCategory.TRACK, kind = BadgeValueKind.COUNT,
                current = minOf(totalSessions.toDouble(), 1.0), target = 1.0
            ),
            Badge(
                id = "track_regular", name = "Track Regular", description = "Complete 5+ Track Mode sessions.",
                category = BadgeCategory.TRACK, kind = BadgeValueKind.COUNT,
                current = minOf(totalSessions.toDouble(), 5.0), target = 5.0
            ),
            Badge(
                id = "consistency_king", name = "Consistency King", description = "Score 90+ Consistency on any Track Mode session.",
                category = BadgeCategory.TRACK, kind = BadgeValueKind.SCORE,
                current = minOf(bestConsistency.toDouble(), 90.0), target = 90.0
            ),
            Badge(
                id = "lean_machine", name = "Lean Machine", description = "Record a 45°+ max lean angle, on the road or the track.",
                category = BadgeCategory.TRACK, kind = BadgeValueKind.DEGREES,
                current = minOf(bestLean, 45.0), target = 45.0
            ),

            // MARK: Streaks
            Badge(
                id = "week_warrior", name = "Week Warrior", description = "Ride or lap 7 days in a row.",
                category = BadgeCategory.STREAKS, kind = BadgeValueKind.DAYS,
                current = minOf(longestStreak.toDouble(), 7.0), target = 7.0
            ),
            Badge(
                id = "monthly_momentum", name = "Monthly Momentum", description = "Ride or lap 30 days in a row.",
                category = BadgeCategory.STREAKS, kind = BadgeValueKind.DAYS,
                current = minOf(longestStreak.toDouble(), 30.0), target = 30.0
            )
        )
    }

    /**
     * Same badge list as [compute], but with "Smooth Operator" resolved for
     * real: scans every road ride (not track sessions — those get their own
     * Track Score, see analytics/RideAnalyticsEngine.kt's LapAnalyticsEngine)
     * that has a resolvable *local* GPX file, and runs RideAnalyticsEngine
     * over each one off the main thread (Dispatchers.Default — CPU-bound
     * regex parsing, not disk/network-bound) to find the best Ride Score.
     * Call this from a coroutine (e.g. LaunchedEffect in BadgesScreen),
     * never from inside a `remember { }` block on the composition thread —
     * that would parse every ride's GPX synchronously on the UI thread.
     */
    suspend fun computeAsync(filesDir: File, rides: List<RideRecord>): List<Badge> {
        val bestRideScore = withContext(Dispatchers.Default) {
            rides
                .filterNot { it.isTrackSession }
                .mapNotNull { it.gpxFileName }
                .map { GPXStorage.resolve(filesDir, it) }
                .filter { it.exists() }
                .mapNotNull { RideAnalyticsEngine.analyze(it)?.rideScore }
                .maxOrNull() ?: 0
        }
        return compute(rides, bestRideScore)
    }

    // 9pm through 5am local, inclusive of 9pm, exclusive of 5am — iOS parity.
    private fun isNightRide(dateMs: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = dateMs }.get(Calendar.HOUR_OF_DAY)
        return hour >= 21 || hour < 5
    }

    // MARK: - Streak Engine (StreakEngine.swift/RidingDigestView.swift port,
    // "longest streak" half only — BadgesView is the only Android consumer).
    private fun activeDays(rides: List<RideRecord>): Set<Long> =
        rides.map { epochDay(it.dateMs) }.toSet()

    private fun epochDay(dateMs: Long): Long =
        Instant.ofEpochMilli(dateMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun longestStreak(activeDays: Set<Long>): Int {
        if (activeDays.isEmpty()) return 0
        val sortedDays = activeDays.sorted()
        var longest = 1
        var current = 1
        for (i in 1 until sortedDays.size) {
            current = if (sortedDays[i] == sortedDays[i - 1] + 1) current + 1 else 1
            longest = maxOf(longest, current)
        }
        return longest
    }

    // MARK: - Consistency (LapAnalyticsEngine.consistencyScore port)
    // 0-100, scored by *percentage* spread (coefficient of variation) rather
    // than raw seconds — a 2-second spread matters a lot more on a 45-second
    // lap than a 3-minute one. Tuned so ~10% spread lands around 60 and a
    // couple percent spread reads as 90+.
    private fun consistencyScore(laps: List<Double>): Int {
        val mean = laps.average()
        if (mean <= 0) return 100
        val coefficientOfVariation = stdDev(laps) / mean
        val score = 100 - (coefficientOfVariation * 400).roundToInt()
        return score.coerceIn(0, 100)
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance)
    }
}
