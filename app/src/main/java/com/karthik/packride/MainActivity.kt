package com.karthik.packride

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.ads.AdManager
import com.karthik.packride.community.PendingCommunityJoin
import com.karthik.packride.crash.CrashDetectionManager
import com.karthik.packride.data.MeasurementUnits
import com.karthik.packride.group.PendingGroupRide
import com.karthik.packride.help.HelpRequestManager
import com.karthik.packride.nav.PendingCrashIncident
import com.karthik.packride.nav.PendingNotificationDestination
import com.karthik.packride.ui.navigation.PackRideNav
import com.karthik.packride.ui.screens.LoginScreen
import com.karthik.packride.ui.screens.OnboardingScreen
import com.karthik.packride.ui.theme.PackRideTheme
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MeasurementUnits.initialize(applicationContext)
        enableEdgeToEdge()
        handleDeepLink(intent)
        // Re-arm explicit safety sessions on a user-visible cold launch.
        // The sticky foreground service handles OS recreation; doing this
        // here also covers force-stop/relaunch without attempting to start a
        // foreground service from a background-only FCM process.
        if (CrashDetectionManager.shouldRestore(this)) CrashDetectionManager.get().startMonitoring()
        if (HelpRequestManager.shouldRestore(this)) HelpRequestManager.get().restoreIfNeeded()
        setContent {
            val darkMode by ThemePreference.get().override.collectAsState()
            PackRideTheme(darkTheme = darkMode ?: false) {
                LaunchedEffect(Unit) { AdManager.gatherConsent(this@MainActivity) }
                val auth = remember { AuthManager(applicationContext) }
                val loading by auth.isLoading.collectAsState()
                val loggedIn by auth.isLoggedIn.collectAsState()
                val onboarded by auth.hasCompletedOnboarding.collectAsState()

                when {
                    loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrCoral)
                        }
                    }
                    !loggedIn -> LoginScreen(auth)
                    !onboarded -> OnboardingScreen(auth)
                    else -> PackRideNav(auth)
                }
            }
        }
    }

    // Aug 30, 2026 — was entirely missing: the manifest's intent-filters
    // (packride://join and packride://joincommunity, see AndroidManifest.xml)
    // only ever got the app to LAUNCH on a tapped link, nothing parsed the
    // resulting Intent's data Uri, so both links opened straight to Home and
    // silently dropped the code/id/passcode. This is the receiving half —
    // Kotlin equivalent of iOS's DeepLinkRouter.handle(url:) in
    // PackRideApp.swift. onNewIntent covers a link tapped while the app is
    // already running (MainActivity is launchMode="singleTop" precisely so
    // this fires instead of a second Activity instance getting created with
    // its own separate Compose state); onCreate's call above covers a cold
    // start from the link.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getStringExtra("packrideType") == "crashIncident") {
            intent.getStringExtra("incidentID")?.takeIf { it.isNotBlank() }?.let(PendingCrashIncident::request)
        } else {
            intent?.getStringExtra("packrideType")?.takeIf { it.isNotBlank() }
                ?.let(PendingNotificationDestination::request)
        }
        val uri = intent?.data ?: return
        if (uri.scheme != "packride") return
        when (uri.host) {
            "join" -> {
                val code = uri.getQueryParameter("code")?.takeIf { it.isNotEmpty() }
                if (code != null) PendingGroupRide.request(code.uppercase(), isLeader = false)
            }
            "joincommunity" -> {
                val id = uri.getQueryParameter("id") ?: ""
                val passcode = uri.getQueryParameter("passcode") ?: ""
                if (id.isNotEmpty() && passcode.isNotEmpty()) {
                    PendingCommunityJoin.request(id.uppercase(), passcode)
                }
            }
        }
    }
}
