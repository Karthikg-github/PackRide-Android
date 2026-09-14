package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthik.packride.friends.FollowRequest
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar

/** Android port of iOS NotificationCenterView, backed by the same Firebase paths. */
@Composable
fun NotificationsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { FriendsManager(context) }
    val requests by manager.followRequests.collectAsState()
    var approvalError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { manager.start() }
    DisposableEffect(Unit) { onDispose { manager.stop() } }

    Column(Modifier.fillMaxSize().background(Pr.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Pr.cardBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(48.dp))
            Text(
                "Notifications",
                color = Pr.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDone) { Text("Done", color = Pr.coral) }
        }

        if (requests.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(74.dp).clip(CircleShape).background(Pr.coralSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(26.dp))
                    }
                    Text("You're all caught up", color = Pr.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Follow requests will show up here.", color = Pr.muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(requests, key = { it.id }) { request ->
                    FollowRequestRow(
                        request = request,
                        onAccept = { manager.acceptFollowRequest(request.id) { approvalError = it } },
                        onDecline = { manager.declineFollowRequest(request.id) }
                    )
                }
            }
        }
    }

    approvalError?.let { message ->
        AlertDialog(
            onDismissRequest = { approvalError = null },
            title = { Text("Couldn't Approve Request") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { approvalError = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun FollowRequestRow(request: FollowRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Pr.fieldBg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrAvatar(initials = request.initials, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(request.name, color = Pr.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Wants to follow you", color = Pr.muted, fontSize = 12.sp)
        }
        IconButton(
            onClick = onAccept,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2E9E5B))
        ) {
            Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        IconButton(
            onClick = onDecline,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Pr.muted)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
