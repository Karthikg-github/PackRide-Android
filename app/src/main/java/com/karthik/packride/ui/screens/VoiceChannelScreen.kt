package com.karthik.packride.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.voice.VoiceChannelManager
import com.karthik.packride.voice.VoiceChatManager

@Composable
fun VoiceChannelScreen(auth: AuthManager, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val rooms = remember { VoiceChannelManager(context) }
    val voice = remember { VoiceChatManager.get() }
    val code by rooms.code.collectAsState(); val title by rooms.title.collectAsState()
    val status by rooms.status.collectAsState(); val host by rooms.isHost.collectAsState()
    val members by rooms.members.collectAsState(); val requests by rooms.requests.collectAsState()
    val error by rooms.error.collectAsState(); val connected by voice.isConnected.collectAsState()
    val muted by voice.isMuted.collectAsState(); val route by voice.audioRoute.collectAsState()
    var joinCode by remember { mutableStateOf("") }; var roomTitle by remember { mutableStateOf("Ride Comms") }
    val name = auth.prefsSnapshot.riderName.ifBlank { "Rider" }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) rooms.connectAudio() }
    fun connect() { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) rooms.connectAudio() else mic.launch(Manifest.permission.RECORD_AUDIO) }

    Column(Modifier.fillMaxSize().background(Pr.bg), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("RIDE COMMS", color = PrCoral, fontWeight = FontWeight.Bold); Text("Private rider voice", style = MaterialTheme.typography.headlineSmall) }
            TextButton(onClick = onDone) { Text("Done") }
        }
        if (status == "idle" || status == "working") {
            HorizontalDivider(color = Pr.border)
            Column(Modifier.fillMaxWidth().background(Pr.cardBg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Start a room", fontWeight = FontWeight.Bold)
                OutlinedTextField(roomTitle, { roomTitle = it.take(80) }, label = { Text("Room name") }, modifier = Modifier.fillMaxWidth())
                Button({ rooms.create(name, roomTitle) }, enabled = status != "working", modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrCoral)) { Text("Create Ride Comms") }
            }
            HorizontalDivider(color = Pr.border)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Pr.border)
            Column(Modifier.fillMaxWidth().background(Pr.cardBg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Join with a code", fontWeight = FontWeight.Bold)
                OutlinedTextField(joinCode, { joinCode = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, label = { Text("6-character code") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton({ rooms.requestJoin(joinCode, name) }, enabled = joinCode.length == 6 && status != "working", modifier = Modifier.fillMaxWidth()) { Text("Request to Join") }
            }
            HorizontalDivider(color = Pr.border)
            Text("People who are not friends can join with your code, but the host must approve them. Location is never shared by joining voice.", color = Pr.muted, modifier = Modifier.padding(18.dp))
        } else {
            HorizontalDivider(color = Pr.border)
            Column(Modifier.fillMaxWidth().background(Color(0xFF171719)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text("ROOM $code", color = PrCoral, fontWeight = FontWeight.Bold)
                if (host) Button({ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Join my PackRide Ride Comms room: $code") }, "Invite riders")) }) { Text("Invite / Share Code") }
                when (status) {
                    "pending" -> Text("Waiting for the host to approve you…", color = Color.White)
                    "rejected" -> Text("The host declined this request. You can leave and request again later.", color = Color.White)
                    "ended" -> Text("This room has ended.", color = Color.White)
                    "accepted" -> {
                        Button({ if (connected) voice.toggleMute() else connect() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (connected && !muted) Color(0xFF2E9E5B) else PrCoral)) { Text(if (!connected) "Join Voice" else if (muted) "Unmute" else "Mute") }
                        if (connected) OutlinedButton({ voice.toggleSpeaker() }, modifier = Modifier.fillMaxWidth()) { Text("Audio: $route") }
                    }
                }
            }
            HorizontalDivider(color = Pr.border)
            if (host && requests.isNotEmpty()) { Text("Waiting room", fontWeight = FontWeight.Bold); requests.forEach { req -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(req.name); Row { TextButton({ rooms.respond(req.id, false) }) { Text("Decline") }; Button({ rooms.respond(req.id, true) }) { Text("Approve") } } } } }
            Text("Riders (${members.size})", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f)) { items(members, key = { it.id }) { Text((if (it.role == "host") "★ " else "") + it.name, modifier = Modifier.fillMaxWidth().padding(10.dp)) } }
            OutlinedButton({ rooms.leave(); onDone() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) { Text(if (host) "End Room" else "Leave Room") }
        }
    }
    if (error != null) AlertDialog(onDismissRequest = rooms::dismissError, confirmButton = { TextButton(rooms::dismissError) { Text("OK") } }, title = { Text("Ride Comms") }, text = { Text(error ?: "") })
}
