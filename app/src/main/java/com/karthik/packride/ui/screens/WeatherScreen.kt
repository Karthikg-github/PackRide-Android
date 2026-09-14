package com.karthik.packride.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrMetricStrip
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrWebSectionLabel
import com.karthik.packride.ui.theme.PrimaryButton
import com.karthik.packride.weather.HourlyPoint
import com.karthik.packride.weather.WeatherManager
import com.karthik.packride.weather.WeatherSnapshot
import java.util.Locale

// Riding-condition safety colors — ported from iOS's RideWeatherInfo.safetyColor
// (WeatherManager.swift): system red/orange, plus the exact custom green iOS
// uses for "Great Riding" (0.373, 0.851, 0.541 -> #5FD98A).
private val SafetyRed = Color(0xFFFF3B30)
private val SafetyYellow = Color(0xFFFF9500)
private val SafetyGreen = Color(0xFF5FD98A)

private fun safetyColorFor(code: String): Color = when (code) {
    "red" -> SafetyRed
    "yellow" -> SafetyYellow
    else -> SafetyGreen
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val locMgr = remember { SharedLocationManager.get() }
    val location by locMgr.location.collectAsState()

    var snap by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var locationName by remember { mutableStateOf("Current Location") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val hasPermission = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            locMgr.onPermissionGranted()
        } else {
            errorText = "Location permission needed for weather"
        }
    }

    LaunchedEffect(Unit) {
        locMgr.startUpdating(SharedLocationManager.REASON_WEATHER)
    }

    // Fetch whenever a fresh GPS fix lands (mirrors iOS WeatherStrip's
    // onChange-of-location kickoff) or the rider taps Refresh.
    LaunchedEffect(location, refreshTick) {
        val loc = location
        if (loc == null) {
            if (!hasPermission) errorText = "Location permission needed for weather"
            return@LaunchedEffect
        }
        isLoading = true
        errorText = null

        @Suppress("DEPRECATION")
        val resolvedName = runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.locality
        }.getOrNull()
        locationName = resolvedName ?: "Current Location"

        val fetched = WeatherManager.fetch(loc)
        isLoading = false
        if (fetched == null) {
            errorText = "Weather unavailable — try again shortly"
        } else {
            snap = fetched
        }
    }

    Box(Modifier.fillMaxSize().background(Pr.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PrPageHeader(
                eyebrow = "Weather",
                title = "Weather",
                subtitle = "Conditions for your ride"
            )

            val current = snap
            if (current == null) {
                if (isLoading) {
                    LoadingBlock()
                } else {
                    EmptyBlock(
                        message = errorText ?: "Getting your location...",
                        needsPermission = !hasPermission,
                        onRequestPermission = { launcher.launch(LOCATION_PERMISSIONS) },
                        onRetry = { refreshTick++ }
                    )
                }
            } else {
                CurrentConditionsHero(current, locationName)
                Spacer(Modifier.height(12.dp))
                RidingSafetyBanner(current)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PrMetricStrip(
                        metrics = listOf(
                            "${com.karthik.packride.data.MeasurementUnits.speedMph(current.windMph)} ${current.windDirection}" to "Wind",
                            "${current.precipChance}%" to "Rain",
                            "${current.humidity}%" to "Humidity"
                        )
                    )
                }
                if (current.hourly.isNotEmpty()) {
                    PrWebSectionLabel(title = "Next ${current.hourly.size} Hours")
                    HourlyForecastRow(current.hourly)
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PrimaryButton(
                        text = if (isLoading) "Refreshing..." else "Refresh Weather",
                        onClick = { refreshTick++ },
                        enabled = !isLoading
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CurrentConditionsHero(w: WeatherSnapshot, locationName: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        PrCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Pr.muted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(locationName, style = PrFont.bodySmall.copy(color = Pr.muted), maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (w.condition == "Clear") {
                        Icon(
                            Icons.Filled.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFFFBF33),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(com.karthik.packride.data.MeasurementUnits.temperatureF(w.tempF), style = PrFont.statLarge)
                }
                Spacer(Modifier.height(4.dp))
                Text(w.condition, style = PrFont.subheading.copy(color = Pr.muted))
                Spacer(Modifier.height(2.dp))
                Text("Feels like ${com.karthik.packride.data.MeasurementUnits.temperatureF(w.feelsLikeF)}", style = PrFont.caption)
            }
        }
    }
}

@Composable
private fun RidingSafetyBanner(w: WeatherSnapshot) {
    val color = safetyColorFor(w.safetyColor)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Pr.RadiusMedium))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(Pr.RadiusMedium))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (w.safetyColor == "green") Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(w.safetyLabel, style = PrFont.bodySmall.copy(color = color, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun HourlyForecastRow(hourly: List<HourlyPoint>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(hourly) { hour ->
            val rainy = hour.precipChance > 30
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(Pr.RadiusSmall))
                    .background(if (rainy) SafetyYellow.copy(alpha = 0.08f) else Pr.cardBg)
                    .border(
                        1.dp,
                        if (rainy) SafetyYellow.copy(alpha = 0.25f) else Pr.border,
                        RoundedCornerShape(Pr.RadiusSmall)
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(hour.hourLabel, style = PrFont.micro)
                Spacer(Modifier.height(4.dp))
                Text("${hour.tempF.toInt()}°", style = PrFont.body.copy(fontWeight = FontWeight.Bold))
                if (hour.precipChance > 10) {
                    Spacer(Modifier.height(2.dp))
                    Text("${hour.precipChance}%", style = PrFont.micro.copy(color = SafetyYellow))
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Pr.coral)
        Spacer(Modifier.height(12.dp))
        Text("Loading weather...", style = PrFont.bodySmall.copy(color = Pr.muted))
    }
}

@Composable
private fun EmptyBlock(
    message: String,
    needsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.WbSunny,
            contentDescription = null,
            tint = Pr.muted,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = PrFont.bodySmall.copy(color = Pr.muted),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = if (needsPermission) "Enable Location" else "Try Again",
            onClick = if (needsPermission) onRequestPermission else onRetry
        )
    }
}
