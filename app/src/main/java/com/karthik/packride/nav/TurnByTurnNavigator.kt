package com.karthik.packride.nav

import android.content.Context
import android.location.Location
import android.speech.tts.TextToSpeech
import com.google.android.gms.maps.model.LatLng
import com.karthik.packride.waypoints.DirectionsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.max

enum class NavState { IDLE, CALCULATING, NAVIGATING, REROUTING, ARRIVED }

data class RouteStep(
    val id: String = UUID.randomUUID().toString(),
    val instruction: String,
    val distanceMeters: Double,
    val lat: Double,
    val lng: Double,
    // Directions API's own maneuver type (e.g. "turn-left", "roundabout-left"),
    // blank for steps that don't have one (many "continue straight" steps).
    val maneuver: String = "",
    val isCompleted: Boolean = false
)

/**
 * Kotlin port of iOS NavigationManager — real turn-by-turn state machine:
 * route calculation (via DirectionsService.routeWithSteps), position-driven
 * step advancement, off-route detection + reroute, arrival detection, and
 * spoken guidance (Android TextToSpeech standing in for AVSpeechSynthesizer).
 * UI-framework-agnostic on purpose (no Compose imports) — TurnByTurnScreen
 * observes these StateFlows and does its own formatting/rendering.
 */
class TurnByTurnNavigator(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(NavState.IDLE)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val _routeError = MutableStateFlow<String?>(null)
    val routeError: StateFlow<String?> = _routeError.asStateFlow()

    private val _steps = MutableStateFlow<List<RouteStep>>(emptyList())
    val steps: StateFlow<List<RouteStep>> = _steps.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    // One polyline per leg — same "don't collapse to just the first leg"
    // fix iOS's NavigationManager needed (see its routePolylines comment).
    private val _routePolylines = MutableStateFlow<List<List<LatLng>>>(emptyList())
    val routePolylines: StateFlow<List<List<LatLng>>> = _routePolylines.asStateFlow()

    private val _distanceRemainingMeters = MutableStateFlow(0.0)
    val distanceRemainingMeters: StateFlow<Double> = _distanceRemainingMeters.asStateFlow()

    private val _etaMinutes = MutableStateFlow(0)
    val etaMinutes: StateFlow<Int> = _etaMinutes.asStateFlow()

    private val _distanceToNextStepMeters = MutableStateFlow(0.0)
    val distanceToNextStepMeters: StateFlow<Double> = _distanceToNextStepMeters.asStateFlow()

    private val _isOffRoute = MutableStateFlow(false)
    val isOffRoute: StateFlow<Boolean> = _isOffRoute.asStateFlow()

    private val _isVoiceEnabled = MutableStateFlow(true)
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    private var legRoutePoints: List<LatLng> = emptyList()
    private var destination: LatLng? = null
    private var remainingWaypoints: List<LatLng> = emptyList()
    private val announcedSteps = mutableSetOf<Int>()
    private var routeJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            // setLanguage(...) returns an Int result code, not Unit, so it
            // doesn't qualify for Kotlin's getter/setter-to-property
            // synthesis — call it directly rather than `tts.language = ...`.
            if (ttsReady) tts?.setLanguage(Locale.US)
        }
    }

    val currentStep: RouteStep? get() = _steps.value.getOrNull(_currentStepIndex.value)
    val nextStep: RouteStep? get() = _steps.value.getOrNull(_currentStepIndex.value + 1)

    fun calculateRoute(from: LatLng, to: LatLng, waypoints: List<LatLng> = emptyList()) {
        _state.value = NavState.CALCULATING
        _routeError.value = null
        destination = to
        remainingWaypoints = waypoints
        routeJob?.cancel()
        routeJob = scope.launch {
            val allPoints = listOf(from) + waypoints + listOf(to)
            val result = DirectionsService.routeWithSteps(appContext, allPoints)
            if (result.legs.isEmpty()) {
                _routeError.value = "A road route could not be calculated. Check your connection and try again."
                _state.value = NavState.IDLE
                return@launch
            }
            _routePolylines.value = result.legs.map { it.polyline }
            legRoutePoints = result.legs.flatMap { it.polyline }
            _steps.value = result.legs.flatMap { it.steps }.map {
                RouteStep(
                    instruction = it.instruction,
                    distanceMeters = it.distanceMeters,
                    lat = it.endLat,
                    lng = it.endLng,
                    maneuver = it.maneuver
                )
            }
            _distanceRemainingMeters.value = result.legs.sumOf { it.distanceMeters }
            _etaMinutes.value = max(1, (result.legs.sumOf { it.durationSeconds } / 60.0).toInt())
            _currentStepIndex.value = 0
            announcedSteps.clear()
            _isOffRoute.value = false
            _state.value = if (_steps.value.isEmpty()) NavState.IDLE else NavState.NAVIGATING
            if (_state.value == NavState.NAVIGATING) announceCurrentStep()
        }
    }

    /** Call on every GPS update while a nav session is on screen. */
    fun updatePosition(location: Location) {
        if (_state.value != NavState.NAVIGATING || _steps.value.isEmpty()) return

        destination?.let { dest ->
            if (distanceMeters(location.latitude, location.longitude, dest.latitude, dest.longitude) < ARRIVAL_DISTANCE_M) {
                arrive()
                return
            }
        }

        currentStep?.let { step ->
            val d = distanceMeters(location.latitude, location.longitude, step.lat, step.lng)
            _distanceToNextStepMeters.value = d
            if (d < STEP_COMPLETION_DISTANCE_M) {
                completeCurrentStep()
            } else if (d < EARLY_WARNING_DISTANCE_M && _currentStepIndex.value !in announcedSteps) {
                announceCurrentStep()
            }
        }

        if (legRoutePoints.isNotEmpty()) {
            val minDist = legRoutePoints.minOf {
                distanceMeters(location.latitude, location.longitude, it.latitude, it.longitude)
            }
            if (minDist > OFF_ROUTE_DISTANCE_M && !_isOffRoute.value) {
                _isOffRoute.value = true
                reroute(LatLng(location.latitude, location.longitude))
            } else if (minDist < OFF_ROUTE_DISTANCE_M) {
                _isOffRoute.value = false
            }
        }

        currentStep?.let { step ->
            val distToStep = distanceMeters(location.latitude, location.longitude, step.lat, step.lng)
            val remainingStepsDist = _steps.value.drop(_currentStepIndex.value + 1).sumOf { it.distanceMeters }
            _distanceRemainingMeters.value = distToStep + remainingStepsDist
            val speed = if (location.hasSpeed() && location.speed > 0f) location.speed.toDouble() else 13.4
            _etaMinutes.value = max(1, (_distanceRemainingMeters.value / speed / 60.0).toInt())
        }
    }

    private fun completeCurrentStep() {
        val idx = _currentStepIndex.value
        val list = _steps.value
        if (idx !in list.indices) return
        _steps.value = list.mapIndexed { i, s -> if (i == idx) s.copy(isCompleted = true) else s }
        if (idx < list.size - 1) {
            _currentStepIndex.value = idx + 1
            announceCurrentStep()
        } else {
            arrive()
        }
    }

    private fun arrive() {
        _state.value = NavState.ARRIVED
        speak("You have arrived at your destination.")
    }

    private fun reroute(from: LatLng) {
        _state.value = NavState.REROUTING
        speak("Recalculating route.")
        val dest = destination ?: return
        calculateRoute(from, dest, remainingWaypoints)
    }

    fun announceCurrentStep() {
        val step = currentStep ?: return
        announcedSteps.add(_currentStepIndex.value)
        val distText = "In ${com.karthik.packride.data.MeasurementUnits.distanceMeters(step.distanceMeters)}"
        speak("$distText, ${step.instruction}")
    }

    fun speak(text: String) {
        if (!_isVoiceEnabled.value || !ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_${System.currentTimeMillis()}")
    }

    fun toggleVoice() {
        _isVoiceEnabled.value = !_isVoiceEnabled.value
        if (!_isVoiceEnabled.value) tts?.stop()
    }

    fun stopNavigation() {
        routeJob?.cancel()
        _state.value = NavState.IDLE
        _routeError.value = null
        _steps.value = emptyList()
        _currentStepIndex.value = 0
        _routePolylines.value = emptyList()
        legRoutePoints = emptyList()
        announcedSteps.clear()
        tts?.stop()
    }

    /** Call from the screen's DisposableEffect — releases the TTS engine for good. */
    fun shutdown() {
        stopNavigation()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val STEP_COMPLETION_DISTANCE_M = 40.0
        private const val EARLY_WARNING_DISTANCE_M = 200.0
        private const val OFF_ROUTE_DISTANCE_M = 100.0
        private const val ARRIVAL_DISTANCE_M = 50.0

        private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            return results[0].toDouble()
        }
    }
}
