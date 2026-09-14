package com.karthik.packride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.invite.RideInviteManager
import com.karthik.packride.ui.theme.PrCoral

@Composable
fun InvitesScreen(auth: AuthManager) {
    val context = LocalContext.current
    val invitesMgr = remember { RideInviteManager(context) }
    val friendsMgr = remember { FriendsManager(context) }
    val invites by invitesMgr.invites.collectAsState()
    val following by friendsMgr.followedUsers.collectAsState()
    var rideCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        invitesMgr.listen()
        friendsMgr.start()
    }
    DisposableEffect(Unit) {
        onDispose {
            invitesMgr.stop()
            friendsMgr.stop()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ride invites", style = MaterialTheme.typography.headlineMedium, color = PrCoral)
        OutlinedTextField(
            rideCode,
            { rideCode = it.uppercase() },
            label = { Text("Ride code to share") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Send to a friend you follow:")
        following.take(10).forEach { f ->
            OutlinedButton(
                onClick = {
                    if (rideCode.length < 4) {
                        status = "Enter a valid ride code"
                        return@OutlinedButton
                    }
                    invitesMgr.sendInvite(f.id, rideCode, auth.prefsSnapshot.riderName)
                    status = "Invite sent to ${f.name}"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Invite ${f.name}") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)

        Text("Inbox", style = MaterialTheme.typography.titleMedium)
        if (invites.isEmpty()) {
            Text("No invites yet.", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(invites, key = { it.id }) { inv ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${inv.fromName} invited you", style = MaterialTheme.typography.titleSmall)
                        Text("Code ${inv.rideCode}")
                        if (inv.message.isNotBlank()) Text(inv.message)
                        OutlinedButton(onClick = { invitesMgr.dismiss(inv.id) }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
