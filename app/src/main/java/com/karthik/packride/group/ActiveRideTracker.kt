package com.karthik.packride.group

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide "am I currently in a group ride, and which one" tracker — Android
 * port of iOS's @AppStorage("activeRideCode"). Need Help's "Share with Group
 * Ride" option (see NeedHelpScreen) and the app-wide incoming-alert banner
 * (see PackRideNav) both need this from outside GroupRideScreen/
 * GroupRideSession, which only ever existed as a per-screen `remember{}`
 * instance with no persisted, globally-readable ride code — this is the gap
 * that filled.
 *
 * Persisted (SharedPreferences, not just in-memory) so the value survives a
 * process restart while a ride is active, same durability iOS gets for free
 * from @AppStorage.
 */
class ActiveRideTracker private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("packride_activeride", Context.MODE_PRIVATE)

    private val _activeRideCode = MutableStateFlow(prefs.getString(KEY, "") ?: "")
    val activeRideCode: StateFlow<String> = _activeRideCode.asStateFlow()

    fun set(code: String) {
        _activeRideCode.value = code
        prefs.edit().putString(KEY, code).apply()
    }

    fun clear() {
        _activeRideCode.value = ""
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "activeRideCode"

        @Volatile
        private var instance: ActiveRideTracker? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = ActiveRideTracker(context.applicationContext)
                    }
                }
            }
        }

        fun get(): ActiveRideTracker =
            instance ?: error("Call ActiveRideTracker.init(Application) first")
    }
}
