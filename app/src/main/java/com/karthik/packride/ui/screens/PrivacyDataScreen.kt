package com.karthik.packride.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthik.packride.ads.AdManager
import com.karthik.packride.moderation.BlockedRider
import com.karthik.packride.moderation.ModerationManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrWebSectionLabel

@Composable
fun PrivacyDataScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val privacyOptionsRequired by AdManager.privacyOptionsRequired.collectAsState()
    val moderation = remember { ModerationManager(context) }
    val blockedRiders by moderation.blockedRiders.collectAsState()
    var showBlocked by remember { mutableStateOf(false) }
    var unblockError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        moderation.start()
        onDispose { moderation.stop() }
    }

    Column(Modifier.fillMaxSize().background(Pr.bg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().background(Pr.cardBg).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Privacy & Data", style = PrFont.heading, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done", color = Pr.coral) }
        }

        PolicySection("What PackRide collects", listOf(
            "Account: email, rider name, optional bike, city, avatar, and cover photo.",
            "Precise location while you track a ride, navigate, join a group ride, use Need Help, or enable crash protection.",
            "Motion-sensor readings for crash detection and optional ride telemetry.",
            "Ride records, GPX routes, communities, follow relationships, posts, reactions, comments, and invitations you create.",
            "Microphone audio only while you deliberately join group voice chat."
        ))
        PolicySection("How it is used", listOf(
            "Firebase provides authentication, synchronized app data, files, notifications, and safety-alert delivery.",
            "Google Maps and Places provide maps, routing, and place search.",
            "Agora carries live group voice audio. PackRide does not record voice calls.",
            "Google AdMob provides advertising. Ads are requested only after the applicable privacy-consent flow permits them."
        ))
        PolicySection("Location sharing", listOf(
            "Follower and community location sharing is off unless you enable it.",
            "Selected followers receive location through private recipient-specific feeds.",
            "Active group-ride members can see one another while in the same room.",
            "Need Help and crash information is sent only to the audience or emergency contacts you choose."
        ))
        PolicySection("Your controls", listOf(
            "Change follower/community sharing and nearby radius from Profile.",
            "Stop crash protection from the app or its persistent notification.",
            "Delete your account and associated synchronized data from Profile.",
            "Change system permissions at any time in Android Settings."
        ))

        PrWebSectionLabel(title = "Privacy controls")
        PrCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrivacyRow("Android permissions") {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }
                PrivacyRow("Blocked riders (${blockedRiders.size})") { showBlocked = true }
                if (privacyOptionsRequired) {
                    PrivacyRow("Ad privacy choices") {
                        (context as? Activity)?.let { AdManager.showPrivacyOptions(it) }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showBlocked) {
        BlockedRidersDialog(
            riders = blockedRiders,
            onDismiss = { showBlocked = false },
            onUnblock = { rider -> moderation.unblock(rider.id) { unblockError = it } }
        )
    }
    unblockError?.let { message ->
        AlertDialog(
            onDismissRequest = { unblockError = null },
            title = { Text("Couldn't unblock rider") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { unblockError = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun BlockedRidersDialog(
    riders: List<BlockedRider>,
    onDismiss: () -> Unit,
    onUnblock: (BlockedRider) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked riders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (riders.isEmpty()) Text("You haven't blocked anyone.", style = PrFont.bodySmall)
                riders.forEach { rider ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonOff, null, tint = Pr.muted)
                        Text(rider.name, style = PrFont.body, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        TextButton(onClick = { onUnblock(rider) }) { Text("Unblock", color = Pr.coral) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun PolicySection(title: String, lines: List<String>) {
    PrWebSectionLabel(title = title)
    PrCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            lines.forEach { Text("• $it", style = PrFont.bodySmall) }
        }
    }
}

@Composable
private fun PrivacyRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = PrFont.body, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Pr.muted)
    }
}
