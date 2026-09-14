package com.karthik.packride.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.nav.PendingSafetyTab

/** Safety tab: Crash detection + Need Help without adding another bottom-nav item. */
@Composable
fun SafetyHubScreen(auth: AuthManager) {
    var tab by remember { mutableIntStateOf(0) }

    // Aug 31, 2026 — Home hero header's warning-triangle icon (see
    // HomeScreen.kt) requests this tab directly, matching iOS's HomeView,
    // which opens NeedHelpView as a fullScreenCover rather than landing on
    // whichever sub-tab happened to be selected — see nav/PendingSafetyTab.kt.
    val pendingSafetyTab by PendingSafetyTab.pending.collectAsState()
    LaunchedEffect(pendingSafetyTab) {
        pendingSafetyTab?.let {
            tab = it
            PendingSafetyTab.clear()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Crash") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Need Help") })
        }
        when (tab) {
            0 -> CrashDetectionScreen()
            else -> NeedHelpScreen(auth)
        }
    }
}
