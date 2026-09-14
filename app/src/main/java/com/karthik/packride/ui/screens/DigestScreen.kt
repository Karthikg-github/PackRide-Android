package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ride.RideRecord
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrWebSectionLabel
import java.util.Calendar
import java.util.Date

// Aug 31, 2026 -- Kotlin port of iOS RidingDigestView.swift (374 lines),
// reached from Ride History's new "Digest" quick-link (see
// RideHistoryScreen.kt / nav/PendingDigest.kt / PackRideNav.kt's
// Dest.Digest). Pure computation over RideHistoryManager's existing ride
// list -- nothing new is persisted here, same as iOS's own header comment.
//
// Two deliberate substitutions vs. the real iOS source, both flagged:
// - iOS folds two separate lists into StreakEngine (historyManager.rides +
//   a dedicated lapHistoryManager.sessions for track sessions). Android has
//   no separate LapHistoryManager/LapRecord type -- track sessions are
//   RideRecords with a non-empty lapTimes list (the same substitution
//   item 21's Badges work already established), and since those are
//   already part of RideHistoryManager's one list, Android's streak
//   computation needs no merge step at all -- every ride (road or track)
//   is already in `rides`.
// - iOS's Road Riding card shows an avg/best Ride Score pulled from each
//   ride's cached `analytics` field. Android has no cached analytics on
//   RideRecord (RideHistoryScreen.kt's new Ride Score card computes it on
//   demand, per ride, off a local GPX file -- see that file's Aug 31, 2026
//   note) -- doing that for every ride in a whole week/month here would
//   mean scanning every one of their GPX files just to open this screen,
//   a much heavier cost than iOS's simple cached-field read. Deliberately
//   left out of this pass rather than built slow or faked; the per-ride
//   Ride Score card on Ride History already covers the "how smooth was
//   THIS ride" need.

private enum class DigestPeriod(val label: String) { WEEK("Week"), MONTH("Month") }

private object StreakEngine {
    fun activeDays(rides: List<RideRecord>): Set<Long> =
        rides.map { startOfDay(it.dateMs) }.toSet()

    fun longestStreak(activeDays: Set<Long>): Int {
        if (activeDays.isEmpty()) return 0
        val sorted = activeDays.sorted()
        var longest = 1
        var current = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == addDays(sorted[i - 1], 1)) current++ else current = 1
            longest = maxOf(longest, current)
        }
        return longest
    }

    // "No ride yet today" isn't treated as a broken streak -- walks back to
    // yesterday in that case, matching iOS exactly.
    fun currentStreak(activeDays: Set<Long>, now: Long): Int {
        if (activeDays.isEmpty()) return 0
        var day = startOfDay(now)
        if (day !in activeDays) {
            val yesterday = addDays(day, -1)
            if (yesterday !in activeDays) return 0
            day = yesterday
        }
        var streak = 0
        while (day in activeDays) {
            streak++
            day = addDays(day, -1)
        }
        return streak
    }

    // Sorted (not maxByOrNull), for the same deterministic tie-break as iOS:
    // lowest weekday number (earliest in the week) wins a tie.
    fun mostActiveWeekday(activeDays: Set<Long>): String? {
        if (activeDays.isEmpty()) return null
        val counts = mutableMapOf<Int, Int>()
        for (day in activeDays) {
            val cal = Calendar.getInstance().apply { timeInMillis = day }
            val weekday = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday ... 7 = Saturday
            counts[weekday] = (counts[weekday] ?: 0) + 1
        }
        val top = counts.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).first()
        val names = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        return names.getOrNull(top.key - 1)
    }

    private fun startOfDay(ms: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun addDays(ms: Long, delta: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.DAY_OF_MONTH, delta) }
        return cal.timeInMillis
    }
}

@Composable
fun DigestScreen() {
    val context = LocalContext.current
    val history = remember { RideHistoryManager(context) }
    val rides by history.rides.collectAsState()

    var period by remember { mutableStateOf(DigestPeriod.WEEK) }
    val now = remember { System.currentTimeMillis() }

    // MARK: Period boundaries -- "week" = most recent Sunday 00:00 through
    // now; "month" = the 1st of the current calendar month 00:00 through now.
    val startOfWeek = remember(now) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val weekday = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
        cal.add(Calendar.DAY_OF_MONTH, -(weekday - 1))
        cal.timeInMillis
    }
    val startOfMonth = remember(now) {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val periodStart = if (period == DigestPeriod.WEEK) startOfWeek else startOfMonth

    // The previous FULLY COMPLETED week/month -- a like-for-like comparison
    // window, never partial-to-partial, same as iOS.
    val previousStart = remember(periodStart, period) {
        Calendar.getInstance().apply {
            timeInMillis = periodStart
            if (period == DigestPeriod.WEEK) add(Calendar.DAY_OF_MONTH, -7) else add(Calendar.MONTH, -1)
        }.timeInMillis
    }

    val roadRidesThisPeriod = rides.filter { it.dateMs in periodStart..now }
    val roadRidesPreviousPeriod = rides.filter { it.dateMs in previousStart until periodStart }

    val totalMilesThisPeriod = roadRidesThisPeriod.sumOf { it.distanceMiles }
    val totalMilesPreviousPeriod = roadRidesPreviousPeriod.sumOf { it.distanceMiles }
    // null when there's no completed-period baseline to compare against
    // (e.g. this is the rider's first week/month ever) -- nothing honest to
    // show as a percentage in that case, matching iOS.
    val percentChange: Double? = if (totalMilesPreviousPeriod > 0)
        ((totalMilesThisPeriod - totalMilesPreviousPeriod) / totalMilesPreviousPeriod) * 100
    else null

    // Streaks -- full history, independent of the period toggle.
    val activeDays = remember(rides) { StreakEngine.activeDays(rides) }
    val currentStreak = remember(activeDays, now) { StreakEngine.currentStreak(activeDays, now) }
    val longestStreak = remember(activeDays) { StreakEngine.longestStreak(activeDays) }
    val mostActiveWeekday = remember(activeDays) { StreakEngine.mostActiveWeekday(activeDays) }

    val topMph = roadRidesThisPeriod.maxOfOrNull { it.maxSpeedMph } ?: 0.0

    // Track Riding -- Android substitution: a RideRecord with lapTimes.isNotEmpty()
    // stands in for iOS's separate LapRecord (see file header note above).
    val hasAnyTrackSessionEver = rides.any { it.lapTimes.isNotEmpty() }
    val trackSessionsThisPeriod = roadRidesThisPeriod.filter { it.lapTimes.isNotEmpty() }
    val lapCountThisPeriod = trackSessionsThisPeriod.sumOf { it.lapTimes.size }
    val bestLapThisPeriod = trackSessionsThisPeriod.flatMap { it.lapTimes }.filter { it > 0 }.minOrNull()
    val favoriteTrack = trackSessionsThisPeriod
        .filter { it.trackName.isNotEmpty() }
        .groupingBy { it.trackName }
        .eachCount()
        .maxByOrNull { it.value }?.key

    Column(Modifier.fillMaxSize().background(Pr.bg).verticalScroll(rememberScrollState())) {
        PrPageHeader(
            eyebrow = "Riding Digest",
            title = "Your Recap",
            subtitle = "A quick look back at your ${period.label.lowercase()}"
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DigestPeriod.entries.forEach { p ->
                val selected = period == p
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) Pr.coral else Pr.cardBg, RoundedCornerShape(10.dp))
                        .clickable(onClick = { period = p })
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        p.label,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Pr.ink)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        PrWebSectionLabel(title = "Streaks")
        PrCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                DigestStat("$currentStreak", "Current Streak", Modifier.weight(1f))
                DigestStat("$longestStreak", "Longest Streak", Modifier.weight(1f))
                DigestStat(mostActiveWeekday?.take(3) ?: "—", "Top Day", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        PrWebSectionLabel(title = "Road Riding")
        PrCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    DigestStat("${roadRidesThisPeriod.size}", "Rides", Modifier.weight(1f))
                    DigestStat("%.0f".format(totalMilesThisPeriod), "Miles", Modifier.weight(1f))
                    DigestStat("%.0f".format(topMph), "Top Speed", Modifier.weight(1f))
                }
                if (percentChange != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "%s%.0f%% vs last %s".format(if (percentChange >= 0) "+" else "", percentChange, period.label.lowercase()),
                        style = TextStyle(
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (percentChange >= 0) Color(0xFF2E9E5B) else Color(0xFFD33B2C)
                        )
                    )
                }
            }
        }

        // Hidden entirely with zero track sessions ever -- not just zero
        // this period, matching iOS.
        if (hasAnyTrackSessionEver) {
            Spacer(Modifier.height(16.dp))
            PrWebSectionLabel(title = "Track Riding")
            PrCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        DigestStat("${trackSessionsThisPeriod.size}", "Sessions", Modifier.weight(1f))
                        DigestStat("$lapCountThisPeriod", "Laps", Modifier.weight(1f))
                        DigestStat(bestLapThisPeriod?.let { "%.1fs".format(it) } ?: "—", "Best Lap", Modifier.weight(1f))
                    }
                    if (favoriteTrack != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Favorite track: $favoriteTrack",
                            style = TextStyle(fontSize = 12.sp, color = Pr.muted)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun DigestStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
        Text(label, style = TextStyle(fontSize = 10.sp, color = Pr.muted))
    }
}
