package com.karthik.packride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthik.packride.analytics.RideAnalytics
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ui.theme.PrCoral

@Composable
fun AnalyticsScreen() {
    val context = LocalContext.current
    val history = remember { RideHistoryManager(context) }
    val rides by history.rides.collectAsState()
    val s = remember(rides) { RideAnalytics.summarize(rides) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ride Analytics", style = MaterialTheme.typography.headlineMedium, color = PrCoral)
        Text("Rides: ${s.totalRides}")
        Text("Total distance: ${com.karthik.packride.data.MeasurementUnits.distanceMiles(s.totalMiles)}")
        Text("Average ride: ${com.karthik.packride.data.MeasurementUnits.distanceMiles(s.avgMiles)}")
        Text("Top speed: ${com.karthik.packride.data.MeasurementUnits.speedMph(s.maxSpeedMph)}")
        Text("Time in saddle: %dh %dm".format(s.totalDurationSeconds / 3600, (s.totalDurationSeconds % 3600) / 60))
        Text("Group rides: ${s.groupRideCount}")
        Text("Max lean: %.0f°".format(s.maxLean))
    }
}
