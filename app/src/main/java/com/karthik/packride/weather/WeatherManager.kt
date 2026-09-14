package com.karthik.packride.weather

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One hour of forecast — Android port of iOS's HourlyRideForecast
 * (WeatherManager.swift). `timeIso` is Open-Meteo's local ISO time for that
 * hour, e.g. "2026-08-30T14:00" (timezone=auto in the request below makes
 * this already local to the queried coordinate, no conversion needed).
 */
data class HourlyPoint(
    val timeIso: String,
    val tempF: Double,
    val condition: String,
    val precipChance: Int,  // 0-100
    val windMph: Double
) {
    /** "2PM" style label, string-sliced from the ISO hour — no java.time / date-library dependency. */
    val hourLabel: String get() = formatHourLabel(timeIso)
}

data class WeatherSnapshot(
    val tempF: Double,
    val feelsLikeF: Double,
    val condition: String,
    val windMph: Double,
    val windDirection: String,
    val humidity: Int,
    val precipChance: Int,           // 0-100, nearest current hour
    val hourly: List<HourlyPoint> = emptyList()
) {
    // Aug 30, 2026 — ported verbatim from iOS's RideWeatherInfo.safetyColor /
    // .safetyLabel (WeatherManager.swift) so Android's riding-condition
    // guidance uses the exact same thresholds and labels as iOS.
    val safetyColor: String get() = when {
        precipChance > 50 || windMph > 30 -> "red"
        precipChance > 20 || windMph > 20 || tempF < 40 -> "yellow"
        else -> "green"
    }

    val safetyLabel: String get() = when (safetyColor) {
        "red" -> "Poor Conditions"
        "yellow" -> "Use Caution"
        else -> "Great Riding"
    }
}

private fun formatHourLabel(iso: String): String {
    // iso looks like "2026-08-30T14:00" — take just the hour, no date library needed.
    val timePart = iso.substringAfter('T', "")
    val hh = timePart.take(2).toIntOrNull() ?: return iso
    val period = if (hh < 12) "AM" else "PM"
    val h12 = when {
        hh == 0 -> 12
        hh > 12 -> hh - 12
        else -> hh
    }
    return "$h12$period"
}

// Ported from iOS WeatherManager.swift's compassDirection(from:).
private fun compassDirection(degrees: Double): String {
    val dirs = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )
    val raw = ((degrees + 11.25) / 22.5).toInt() % 16
    val index = if (raw < 0) raw + 16 else raw
    return dirs[index]
}

/**
 * Open-Meteo free API (no key) — pragmatic Android stand-in for iOS's
 * WeatherKit-backed WeatherManager.
 *
 * Aug 30, 2026 — extended beyond the original temp/condition/wind/humidity
 * snapshot with feels-like, wind direction, precipitation chance, and a
 * 12-hour forecast, so WeatherScreen can reach the same functional parity
 * as iOS's WeatherDetailCard + HourlyForecastStrip + safety banner
 * (WeatherView.swift / WeatherManager.swift). The two existing callers
 * (WeatherScreen, WaypointsScreen's Weather-Ahead sheet) only read the
 * original fields, so this is purely additive.
 */
object WeatherManager {
    suspend fun fetch(location: Location): WeatherSnapshot? = fetch(location.latitude, location.longitude)

    // Aug 30, 2026 — lat/lng overload added for WaypointsScreen's
    // Weather-Ahead sheet, which needs weather at planned stop coordinates
    // (not the rider's live GPS fix, so no Location object on hand).
    suspend fun fetch(latitude: Double, longitude: Double): WeatherSnapshot? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,apparent_temperature" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m" +
                    "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=2"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val body = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val code = current.optInt("weather_code", 0)

            val hourly = mutableListOf<HourlyPoint>()
            var precipNow = 0
            val hourlyJson = root.optJSONObject("hourly")
            if (hourlyJson != null) {
                val times = hourlyJson.getJSONArray("time")
                val temps = hourlyJson.getJSONArray("temperature_2m")
                val codes = hourlyJson.getJSONArray("weather_code")
                val precips = hourlyJson.getJSONArray("precipitation_probability")
                val winds = hourlyJson.getJSONArray("wind_speed_10m")

                // Find the first hourly slot at/after "now" (both fields come
                // back in the same YYYY-MM-DDTHH:mm local format under
                // timezone=auto, so a plain string comparison finds it).
                val currentTime = current.optString("time", "")
                var startIndex = 0
                for (i in 0 until times.length()) {
                    if (times.optString(i) >= currentTime) {
                        startIndex = i
                        break
                    }
                }
                if (times.length() > 0) {
                    precipNow = precips.optInt(startIndex, 0)
                }

                val end = minOf(startIndex + 12, times.length())
                for (i in startIndex until end) {
                    hourly.add(
                        HourlyPoint(
                            timeIso = times.optString(i),
                            tempF = temps.optDouble(i, 0.0),
                            condition = weatherCodeLabel(codes.optInt(i, 0)),
                            precipChance = precips.optInt(i, 0),
                            windMph = winds.optDouble(i, 0.0)
                        )
                    )
                }
            }

            WeatherSnapshot(
                tempF = current.optDouble("temperature_2m", 0.0),
                feelsLikeF = current.optDouble("apparent_temperature", current.optDouble("temperature_2m", 0.0)),
                condition = weatherCodeLabel(code),
                windMph = current.optDouble("wind_speed_10m", 0.0),
                windDirection = compassDirection(current.optDouble("wind_direction_10m", 0.0)),
                humidity = current.optInt("relative_humidity_2m", 0),
                precipChance = precipNow,
                hourly = hourly
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun weatherCodeLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 61, 63, 65 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        95, 96, 99 -> "Thunder"
        else -> "Mixed"
    }
}
