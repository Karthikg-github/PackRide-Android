package com.karthik.packride.crash

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.karthik.packride.alerts.CrashAlertManager
import com.karthik.packride.data.UserPrefs
import com.karthik.packride.group.ActiveRideTracker
import com.karthik.packride.safety.EmergencyContactStore
import com.karthik.packride.location.SharedLocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Crash detection via accelerometer — Kotlin port of iOS CrashDetectionManager.
 * Uses SharedLocationManager with background reason "crashDetection".
 * Threshold 4g; 30s cancel countdown before emergency action.
 *
 * Aug 30, 2026 — was constructed fresh (`remember { CrashDetectionManager(context) }`)
 * inside CrashDetectionScreen and torn down via `DisposableEffect { onDispose {
 * manager.stopMonitoring() } }`, so navigating away from that screen — switching
 * the Safety tab from Crash to Need Help, or switching bottom tabs entirely —
 * unregistered the sensor listener and silently turned protection back off. On
 * iOS, CrashDetectionManager is only started/stopped by the rider's explicit
 * toggle (see CrashDetectionView.swift's onAppear/onDisappear, which touch
 * nothing related to monitoring) — enabling it is meant to stay on regardless
 * of what screen is showing, the same way SharedLocationManager itself works.
 * Now a true singleton (init/get, same pattern as SharedLocationManager below)
 * so the Composable only observes it; start/stop is driven solely by the
 * rider's protection toggle.
 */
class CrashDetectionManager private constructor(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val locationManager = SharedLocationManager.get()
    private val crashAlerts = CrashAlertManager(context.applicationContext)
    private val emergencyStore = EmergencyContactStore(context.applicationContext)

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _crashDetected = MutableStateFlow(false)
    val crashDetected: StateFlow<Boolean> = _crashDetected.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(30)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _currentGForce = MutableStateFlow(0.0)
    val currentGForce: StateFlow<Double> = _currentGForce.asStateFlow()

    private val _maxGForce = MutableStateFlow(0.0)
    val maxGForce: StateFlow<Double> = _maxGForce.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private val _emergencyTriggered = MutableStateFlow(false)
    val emergencyTriggered: StateFlow<Boolean> = _emergencyTriggered.asStateFlow()

    // Aug 30, 2026 -- CrashDetectionScreen rewrite: monotonic token (mirrors
    // iOS's `emergencyAlertRequestID: UUID?`, which gets a fresh UUID every
    // call so SwiftUI's onChange always fires) alongside emergencyTriggered --
    // a plain Boolean can't signal a SECOND crash within one monitoring
    // session (true -> true is not a state change, so a collector keyed on
    // the boolean alone would silently miss the repeat trigger, e.g. two
    // "Run Test Alert" taps in a row, or a real crash after a cancelled
    // false alarm). The screen keys its SMS-composer LaunchedEffect off this
    // token instead of emergencyTriggered.
    private val _emergencyAlertToken = MutableStateFlow(0L)
    val emergencyAlertToken: StateFlow<Long> = _emergencyAlertToken.asStateFlow()

    private val _smsTargets = MutableStateFlow<List<String>>(emptyList())
    /** Phone numbers to pre-fill in SMS when countdown fires. */
    val smsTargets: StateFlow<List<String>> = _smsTargets.asStateFlow()

    private val _smsBody = MutableStateFlow("")
    val smsBody: StateFlow<String> = _smsBody.asStateFlow()

    private var countdownJob: Job? = null
    private var locationJob: Job? = null
    private var lastUiUpdateMs = 0L
    private var alertCancelled = false

    fun startMonitoring() {
        if (accelerometer == null) return
        if (_isMonitoring.value) return
        // Android 14+ will reject a location foreground service when the
        // location permission has been revoked. Do not pretend protection
        // is active when the process cannot keep its required service alive.
        if (!locationManager.hasLocationPermission()) return

        _isMonitoring.value = true
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, true).apply()
        _crashDetected.value = false
        _maxGForce.value = 0.0
        _emergencyTriggered.value = false
        alertCancelled = false

        locationManager.requestBackgroundUpdates(SharedLocationManager.REASON_CRASH_DETECTION)
        locationJob = scope.launch {
            locationManager.location.collect { loc ->
                _lastLocation.value = loc
            }
        }

        // SENSOR_DELAY_GAME ≈ 20ms; we sample every event and treat ~10Hz useful rate in practice
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        restorePendingCountdownIfNeeded()
    }

    fun stopMonitoring() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()
        sensorManager.unregisterListener(this)
        locationManager.releaseBackgroundUpdates(SharedLocationManager.REASON_CRASH_DETECTION)
        locationJob?.cancel()
        locationJob = null
        cancelCountdown()
        _isMonitoring.value = false
        _crashDetected.value = false
        _currentGForce.value = 0.0
        _maxGForce.value = 0.0
        lastUiUpdateMs = 0L
    }

    fun cancelAlert() {
        alertCancelled = true
        _crashDetected.value = false
        _countdownSeconds.value = 30
        cancelCountdown()
        clearPendingCountdown()
    }

    /** For QA — same as iOS simulateCrash. */
    fun simulateCrash() {
        triggerCrashAlert()
    }

    // Aug 30, 2026 -- CrashDetectionScreen rewrite: iOS's CrashAlertOverlay
    // "Send Alert Now" button calls crashManager.sendEmergencyAlert() -- a
    // PUBLIC method there -- to send immediately without waiting out the
    // countdown. sendEmergencyAlert() here was private (only ever reached
    // via the countdown coroutine finishing), so the UI had no way to wire
    // up an equivalent immediate-send button. This is that entry point --
    // it also cancels the still-running countdown job first, which iOS's
    // own version does not (the iOS countdownTimer keeps ticking after a
    // manual send and can send a second time when it reaches zero); this
    // Kotlin port intentionally does not reproduce that.
    fun sendNow() {
        cancelCountdown()
        sendEmergencyAlert()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        // Android accelerometer is in m/s² including gravity → divide by g
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        // .toDouble() — sqrt(Float) returns Float, but _maxGForce/
        // _currentGForce are StateFlow<Double>; Kotlin doesn't widen
        // implicitly on assignment like Java does.
        val gForce = sqrt(x * x + y * y + z * z).toDouble()

        if (gForce > _maxGForce.value) _maxGForce.value = gForce
        if (gForce > CRASH_THRESHOLD && !_crashDetected.value) {
            triggerCrashAlert()
        }

        val now = System.currentTimeMillis()
        if (now - lastUiUpdateMs >= 500) {
            _currentGForce.value = gForce
            lastUiUpdateMs = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun triggerCrashAlert() {
        scope.launch {
            _crashDetected.value = true
            _countdownSeconds.value = 30
            alertCancelled = false
            val deadline = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1_000L
            persistPendingCountdown(deadline)
            startCountdown(deadline)
        }
    }

    private fun startCountdown(deadlineMs: Long) {
        cancelCountdown()
        countdownJob = scope.launch {
            while (isActive) {
                if (alertCancelled) return@launch
                val remainingMs = deadlineMs - System.currentTimeMillis()
                if (remainingMs <= 0) break
                _countdownSeconds.value = SafetyCountdown.secondsRemaining(deadlineMs, System.currentTimeMillis())
                delay(minOf(1_000L, remainingMs))
            }
            if (!alertCancelled) {
                _countdownSeconds.value = 0
                sendEmergencyAlert()
            }
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun sendEmergencyAlert() {
        clearPendingCountdown()
        _emergencyTriggered.value = true
        _emergencyAlertToken.value = _emergencyAlertToken.value + 1
        _crashDetected.value = false
        val loc = _lastLocation.value
        val contacts = emergencyStore.load()
        val linked = contacts.mapNotNull { it.linkedUserID }.filter { it.isNotBlank() }
        crashAlerts.sendCrashAlerts(
            recipientUIDs = linked,
            senderName = UserPrefs(appContext).riderName.ifBlank { "A PackRide rider" },
            lat = loc?.latitude,
            lng = loc?.longitude,
            peakG = _maxGForce.value,
            groupRideCode = ActiveRideTracker.get().activeRideCode.value.takeIf { it.isNotBlank() }
        )
        val phones = contacts.map { it.phone }.filter { it.isNotBlank() }
        _smsTargets.value = phones
        _smsBody.value = buildString {
            append("PackRide emergency — possible crash detected.")
            if (loc != null) {
                append(" https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
            }
        }
    }

    private fun persistPendingCountdown(deadlineMs: Long) {
        val loc = _lastLocation.value
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_ALERT_DEADLINE, deadlineMs)
            .putFloat(KEY_ALERT_PEAK_G, _maxGForce.value.toFloat())
            .apply {
                if (loc != null) {
                    putLong(KEY_ALERT_LAT_BITS, java.lang.Double.doubleToRawLongBits(loc.latitude))
                    putLong(KEY_ALERT_LNG_BITS, java.lang.Double.doubleToRawLongBits(loc.longitude))
                } else {
                    remove(KEY_ALERT_LAT_BITS).remove(KEY_ALERT_LNG_BITS)
                }
            }
            .apply()
    }

    private fun restorePendingCountdownIfNeeded() {
        if (_crashDetected.value) return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deadline = prefs.getLong(KEY_ALERT_DEADLINE, 0L)
        if (deadline <= 0L) return
        _maxGForce.value = maxOf(_maxGForce.value, prefs.getFloat(KEY_ALERT_PEAK_G, 0f).toDouble())
        if (prefs.contains(KEY_ALERT_LAT_BITS) && prefs.contains(KEY_ALERT_LNG_BITS)) {
            _lastLocation.value = Location("crashRecovery").apply {
                latitude = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_ALERT_LAT_BITS, 0L))
                longitude = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_ALERT_LNG_BITS, 0L))
            }
        }
        alertCancelled = false
        _crashDetected.value = true
        _countdownSeconds.value = SafetyCountdown.secondsRemaining(deadline, System.currentTimeMillis())
        startCountdown(deadline)
    }

    private fun clearPendingCountdown() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_ALERT_DEADLINE)
            .remove(KEY_ALERT_PEAK_G)
            .remove(KEY_ALERT_LAT_BITS)
            .remove(KEY_ALERT_LNG_BITS)
            .apply()
    }

    companion object {
        private const val CRASH_THRESHOLD = 4.0
        private const val COUNTDOWN_SECONDS = 30
        private const val PREFS_NAME = "packride_prefs"
        private const val KEY_ENABLED = "crashProtectionEnabled"
        private const val KEY_ALERT_DEADLINE = "crashAlertDeadlineMs"
        private const val KEY_ALERT_PEAK_G = "crashAlertPeakG"
        private const val KEY_ALERT_LAT_BITS = "crashAlertLatitudeBits"
        private const val KEY_ALERT_LNG_BITS = "crashAlertLongitudeBits"

        fun shouldRestore(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        @Volatile
        private var instance: CrashDetectionManager? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = CrashDetectionManager(context.applicationContext)
                    }
                }
            }
        }

        fun get(): CrashDetectionManager =
            instance ?: error("Call CrashDetectionManager.init(Application) first")
    }
}
