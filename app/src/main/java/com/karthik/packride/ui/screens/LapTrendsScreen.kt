package com.karthik.packride.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthik.packride.lap.LapCompareEngine
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrMetricStrip
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrWebSectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lap trends from stored track sessions — simplified LapTrendsView.
 * Uses ride history entries that carry lapTimes when present.
 */
@Composable
fun LapTrendsScreen() {
    val context = LocalContext.current
    val history = remember { RideHistoryManager(context) }
    val rides by history.rides.collectAsState()
    val allSessions = rides.filter { it.lapTimes.isNotEmpty() }
    val trackNames = allSessions.map { it.trackName.ifBlank { "Track Session" } }.distinct()
    var selectedTrack by remember(trackNames) { mutableStateOf(trackNames.firstOrNull().orEmpty()) }
    val trackSessions = allSessions.filter { it.trackName.ifBlank { "Track Session" } == selectedTrack }.sortedBy { it.dateMs }.takeLast(15)
    val fmt = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Pr.bg)) {
        PrPageHeader("TRACK ANALYTICS", "Progress over time", "Compare pace at the same track")
        if (allSessions.isEmpty()) {
            Text(
                "No track sessions with lap times yet. Finish a Track mode session to populate trends.",
                modifier = Modifier.padding(24.dp), style = PrFont.bodySmall
            )
            return
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            items(trackNames) { name ->
                Text(name, color = if (selectedTrack == name) Color.White else Pr.muted, style = PrFont.bodySmall,
                    modifier = Modifier.background(if (selectedTrack == name) Pr.coral else Pr.cardBg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .clickable { selectedTrack = name }.padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }

        val allBests = trackSessions.mapNotNull { it.lapTimes.minOrNull() }
        val overallBest = allBests.minOrNull()
        val averageScore = trackSessions.mapNotNull { it.trackScore }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        PrMetricStrip(listOf(
            (averageScore?.toString() ?: "--") to "Avg Score",
            (overallBest?.let { LapCompareEngine.formatLap(it) } ?: "--:--") to "Best Lap",
            "${trackSessions.size}" to "Sessions"
        ))

        if (trackSessions.size > 1) {
            PrWebSectionLabel("Pace", "Lower is faster")
            PrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Best Lap Trend", style = PrFont.subheading)
                    TrendLine(trackSessions.mapNotNull { it.lapTimes.minOrNull() }, Pr.coral)
                    Text("Oldest on the left • newest on the right", style = PrFont.caption)
                }
            }
            val scores = trackSessions.mapNotNull { it.trackScore }
            if (scores.size > 1) {
                PrWebSectionLabel("Performance", "0–100")
                PrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Track Score", style = PrFont.subheading)
                        TrendLine(scores.map(Int::toDouble), Pr.teal, fixedHundred = true)
                    }
                }
            }
            val consistency = trackSessions.mapNotNull { it.consistencyScore }
            if (consistency.isNotEmpty()) {
                PrWebSectionLabel("Consistency", "0–100")
                PrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Lap Consistency", style = PrFont.subheading)
                        ScoreBars(consistency)
                    }
                }
            }
        } else {
            Text("Run this track again to unlock the trend graph.", style = PrFont.bodySmall, modifier = Modifier.padding(20.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            items(trackSessions, key = { it.id }) { session ->
                val best = session.lapTimes.minOrNull() ?: 0.0
                val avg = session.lapTimes.average()
                PrCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            session.trackName.ifBlank { "Track session" },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(fmt.format(Date(session.dateMs)), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${session.lapTimes.size} laps · best ${LapCompareEngine.formatLap(best)} · avg ${LapCompareEngine.formatLap(avg)}"
                        )
                        // mini spark: show last up to 5 lap deltas from best
                        val spark = session.lapTimes.takeLast(5).joinToString("  ") {
                            val d = it - best
                            if (d < 0.05) LapCompareEngine.formatLap(it)
                            else "+${"%.2f".format(d)}s"
                        }
                        Text(spark, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendLine(values: List<Double>, color: Color, fixedHundred: Boolean = false) {
    Canvas(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(150.dp)) {
        if (values.size < 2) return@Canvas
        val lo = if (fixedHundred) 0.0 else values.minOrNull() ?: return@Canvas
        val hi = if (fixedHundred) 100.0 else values.maxOrNull() ?: return@Canvas
        val range = (hi - lo).coerceAtLeast(.1)
        val points = values.mapIndexed { index, value ->
            Offset(index * size.width / (values.size - 1), ((value - lo) / range * size.height).toFloat())
        }
        points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 7f) }
        points.forEach { drawCircle(color, 9f, it) }
    }
}

@Composable
private fun ScoreBars(values: List<Int>) {
    Canvas(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(150.dp)) {
        val gap = 8f
        val width = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(3f)
        values.forEachIndexed { index, value ->
            val height = size.height * value.coerceIn(0, 100) / 100f
            drawRect(Color(0xFF2E9E5B), Offset(index * (width + gap), size.height - height), androidx.compose.ui.geometry.Size(width, height))
        }
    }
}
