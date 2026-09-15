package com.karthik.packride

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ServerValue
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.crash.CrashDetectionManager
import com.karthik.packride.group.ActiveRideTracker
import com.karthik.packride.garage.GarageManager
import com.karthik.packride.help.HelpRequestManager
import com.karthik.packride.location.LocationTrackingService
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.notify.PackRideMessagingService
import com.karthik.packride.ui.theme.ThemePreference
import com.karthik.packride.voice.VoiceChatManager
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest

class PackRideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installLocalCrashRecorder()
        FirebaseApp.initializeApp(this)
        uploadPendingCrashFeedbackWhenAuthenticated()
        // Cache shared social/ride data and queue RTDB writes while offline.
        // Must be enabled before any DatabaseReference is created.
        runCatching { FirebaseDatabase.getInstance().setPersistenceEnabled(true) }
        SharedLocationManager.init(this)
        // Aug 31, 2026 — Places SDK init for live address autocomplete
        // (Waypoints) and track-name search (Track Mode), closing the "no
        // live Places Autocomplete" scope gap. Reuses the SAME Google Cloud
        // project/key Maps and Directions already use — read straight out
        // of the manifest meta-data AndroidManifest.xml already populates
        // from local.properties' MAPS_API_KEY (see readMapsApiKey below),
        // so there is no second key to create or manage. Karthik: this
        // needs "Places API (New)" enabled on that same Cloud Console
        // project before autocomplete/place-details calls return real data
        // at runtime — see android-build-plan.md.
        val mapsApiKey = readMapsApiKey()
        if (mapsApiKey.isNotBlank()) {
            Places.initialize(applicationContext, mapsApiKey)
        }
        // Aug 30, 2026 — app-wide singleton (was created fresh per-screen and
        // torn down on navigation, see CrashDetectionManager.kt) so enabling
        // crash protection survives navigating anywhere else in the app.
        CrashDetectionManager.init(this)
        // Aug 30, 2026 — Need Help targeting fix: HelpRequestManager and
        // CommunityMembershipStore become app-wide singletons for the same
        // reason CrashDetectionManager did above (a share, and the incoming-
        // alert listener the new PackRideNav banner depends on, must survive
        // navigation); ActiveRideTracker is new, the Android port of iOS's
        // @AppStorage("activeRideCode"). HelpRequestManager depends on
        // SharedLocationManager, so it must init after it.
        CommunityMembershipStore.init(this)
        ActiveRideTracker.init(this)
        HelpRequestManager.init(this)
        // Aug 30, 2026 — Garage visual+functionality parity pass: app-wide so
        // GarageManager.currentActiveBikeID() static lookup (used when tagging
        // a finished ride with its bike) works from any ride-recording call
        // site without needing a screen-scoped instance around.
        GarageManager.init(this)
        // Aug 30, 2026 — Profile visual+functionality parity pass: real Dark
        // Mode toggle backing store, app-wide so prIsDarkTheme() resolves
        // correctly from any composable before Profile itself has ever been
        // opened this session.
        ThemePreference.init(this)
        // Aug 31, 2026 — Voice chat (group ride intercom): app-wide
        // singleton for the same reason CrashDetectionManager/HelpRequestManager
        // are above — the Agora engine and any live channel membership must
        // survive navigating away from GroupRideScreen while a ride (and its
        // voice channel) is still active, matching iOS's VoiceChatManager
        // living for the app's whole lifetime as an @StateObject above MapView.
        VoiceChatManager.init(this)
        createNotificationChannels()
    }

    /**
     * Keeps the most recent fatal stack trace inside the debug-install's app
     * data. This survives process restart and can be read with
     * `adb shell run-as com.karthik.packride cat files/last_crash.txt`, so an
     * intermittent device-only failure remains diagnosable after the app exits.
     */
    private fun installLocalCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                File(filesDir, "last_crash.txt").writeText(
                    "thread=${thread.name}\ntime=${System.currentTimeMillis()}\n$trace"
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Crashlytics retains the complete diagnostic. The shared feedback path
     * receives a compact, privacy-safe correlation code on the next launch,
     * after Firebase Auth has restored the rider. Network work is deliberately
     * not attempted from the crashing process.
     */
    private fun uploadPendingCrashFeedbackWhenAuthenticated() {
        val crashFile = File(filesDir, "last_crash.txt")
        val crashedPreviously = FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()

        val listener = object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(auth: FirebaseAuth) {
                val user = auth.currentUser ?: return
                FirebaseCrashlytics.getInstance().setUserId(user.uid)
                if (!crashFile.exists() && !crashedPreviously) {
                    auth.removeAuthStateListener(this)
                    return
                }
                val raw = runCatching { crashFile.readText() }.getOrDefault("Crashlytics detected a previous fatal crash")
                val code = "AND-${shortCrashCode(raw)}"
                val summary = raw.lineSequence().take(18).joinToString("\n").take(4_000)
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                val payload = mapOf(
                    "senderUID" to user.uid,
                    "senderName" to "PackRide crash reporter",
                    "senderEmail" to "",
                    "platform" to "android",
                    "type" to "automatic_crash",
                    "errorCode" to code,
                    "appVersion" to (packageInfo.versionName ?: "unknown"),
                    "appBuild" to buildNumber.toString(),
                    "message" to "Automatic crash report $code. Match the authenticated Firebase UID, app build, and report time in Crashlytics.\n\n$summary",
                    "createdAt" to ServerValue.TIMESTAMP
                )
                FirebaseDatabase.getInstance().reference.child("feedback").push().setValue(payload)
                    .addOnSuccessListener {
                        crashFile.delete()
                        auth.removeAuthStateListener(this)
                    }
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
    }

    private fun shortCrashCode(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(5)
            .joinToString("") { "%02X".format(it) }

    // Reads the SAME key AndroidManifest.xml's com.google.android.geo.API_KEY
    // meta-data already carries (populated from local.properties' MAPS_API_KEY
    // via manifestPlaceholders in build.gradle.kts) — no buildConfigField and
    // no second key, just pulling the value back out of the manifest at
    // runtime for the one call (Places.initialize) that needs it as a plain
    // String instead of manifest XML.
    private fun readMapsApiKey(): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            info.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                LocationTrackingService.CHANNEL_ID,
                getString(R.string.location_service_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Shown while PackRide is tracking a ride, laps, Need Help, or crash protection"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                PackRideMessagingService.CHANNEL_SAFETY,
                "Safety alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Crash and Need Help alerts from riders who selected you" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                PackRideMessagingService.CHANNEL_UPDATES,
                "Ride and social updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Follow requests, invitations, and ride updates" }
        )
    }
}
