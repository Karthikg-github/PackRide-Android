package com.karthik.packride.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aug 30, 2026 — Profile screen's real Dark Mode toggle. iOS's ProfileView
 * has a simple @AppStorage("darkModeOn") Bool that hardcodes a default of
 * false (always starts light). Android uses the same default; true/false let
 * Profile select the matching palette. Same app-wide singleton shape as
 * GarageManager/CrashDetectionManager/etc. — init() from PackRideApp.onCreate().
 */
class ThemePreference private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("packride_prefs", Context.MODE_PRIVATE)

    private val _override = MutableStateFlow(readStored())
    val override: StateFlow<Boolean?> = _override.asStateFlow()

    fun set(value: Boolean?) {
        prefs.edit().apply {
            if (value == null) remove(KEY) else putBoolean(KEY, value)
        }.apply()
        _override.value = value
    }

    private fun readStored(): Boolean? =
        if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null

    companion object {
        private const val KEY = "darkModeOverride"

        @Volatile
        private var instance: ThemePreference? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = ThemePreference(context.applicationContext)
                    }
                }
            }
        }

        fun get(): ThemePreference =
            instance ?: error("Call ThemePreference.init(Application) first")
    }
}

/**
 * Resolves the displayed theme from the same persisted, default-light toggle
 * as iOS's darkModeOn @AppStorage.
 */
@Composable
fun prIsDarkTheme(): Boolean {
    val override by ThemePreference.get().override.collectAsState()
    return override ?: false
}
