package com.karthik.packride.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class MeasurementSystem { IMPERIAL, METRIC }

/**
 * App-wide display preference. Persisted ride/track/Firebase values remain in
 * their existing canonical units so Android and iOS data stay compatible.
 */
object MeasurementUnits {
    private const val PREFS = "packride_preferences"
    private const val KEY = "measurementSystem"
    private val mutableSystem = MutableStateFlow(MeasurementSystem.IMPERIAL)
    val system = mutableSystem.asStateFlow()
    val current: MeasurementSystem get() = mutableSystem.value

    fun initialize(context: Context) {
        mutableSystem.value = runCatching {
            MeasurementSystem.valueOf(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, MeasurementSystem.IMPERIAL.name)!!
            )
        }.getOrDefault(MeasurementSystem.IMPERIAL)
    }

    fun set(context: Context, value: MeasurementSystem) {
        mutableSystem.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value.name).apply()
    }

    private fun number(value: Double, decimals: Int) =
        String.format(Locale.getDefault(), "%.${decimals}f", value)

    fun milesToDisplay(miles: Double): Double =
        if (current == MeasurementSystem.METRIC) miles * 1.609344 else miles

    fun displayDistanceToMiles(value: Double): Double =
        if (current == MeasurementSystem.METRIC) value / 1.609344 else value

    val distanceInputLabel: String get() =
        if (current == MeasurementSystem.METRIC) "Kilometres" else "Miles"

    fun distanceMiles(miles: Double, decimals: Int = 1): String =
        if (current == MeasurementSystem.METRIC) "${number(miles * 1.609344, decimals)} km"
        else "${number(miles, decimals)} mi"

    fun speedMph(mph: Double, decimals: Int = 0): String =
        if (current == MeasurementSystem.METRIC) "${number(mph * 1.609344, decimals)} km/h"
        else "${number(mph, decimals)} mph"

    fun distanceMeters(meters: Double, decimals: Int = 0): String = when {
        current == MeasurementSystem.METRIC && meters >= 1_000 -> "${number(meters / 1_000, 1)} km"
        current == MeasurementSystem.METRIC -> "${number(meters, decimals)} m"
        meters >= 1_609.344 -> "${number(meters / 1_609.344, 1)} mi"
        else -> "${number(meters * 3.28084, decimals)} ft"
    }

    fun temperatureF(fahrenheit: Double, decimals: Int = 0): String =
        if (current == MeasurementSystem.METRIC) "${number((fahrenheit - 32) * 5 / 9, decimals)}°C"
        else "${number(fahrenheit, decimals)}°F"

    fun elevationMeters(meters: Double, decimals: Int = 0): String =
        if (current == MeasurementSystem.METRIC) "${number(meters, decimals)} m"
        else "${number(meters * 3.28084, decimals)} ft"
}
