package com.karthik.packride.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.karthik.packride.crash.CrashDetectionManager
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.friends.RiderProfile
import com.karthik.packride.safety.EmergencyContact
import com.karthik.packride.safety.EmergencyContactStore
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrFont

// Aug 30, 2026 — full rewrite from the plain-Material placeholder screen to
// real parity with iOS's CrashDetectionView.swift: the dark gradient
// protection hero, the live G-force monitor strip (shown only while
// monitoring, matching iOS's `if crashManager.isMonitoring`), the shared
// emergency-contacts list (same "emergencyContacts" prefs key as
// EmergencyContactsScreen / MoreScreen's SOS tab — same one-list-everywhere
// fix iOS already made between CrashDetectionView and ProfileView), the
// "How It Works" steps, and the full-screen crash-alert overlay with its
// countdown ring. Wired to the real CrashDetectionManager.get() singleton —
// never a screen-scoped instance (see the bug-fix comment on
// CrashDetectionManager.kt itself).
//
// Deliberate scope skip: iOS's AddContactSheet offers a "Pick from
// Contacts" button (device Contacts picker via ContactsUI). Nothing in this
// Android codebase integrates the system Contacts picker anywhere yet
// (grep-confirmed — EmergencyContactsScreen.kt, the other place this app
// edits the same contact list, doesn't have one either), so this screen's
// add/edit dialog sticks to manual name/phone/relationship entry plus
// linking a followed PackRide friend, matching what's already real
// elsewhere in this app rather than inventing new contacts-permission
// plumbing in a UI-only pass.

private val relationships = listOf("Spouse", "Partner", "Parent", "Sibling", "Child", "Friend", "Other")

@Composable
fun CrashDetectionScreen() {
    val context = LocalContext.current
    // Aug 30, 2026 — app-wide singleton (see CrashDetectionManager.kt) instead
    // of a screen-scoped instance, so turning protection on survives leaving
    // this screen (switching the Safety tab, or bottom tabs entirely) instead
    // of silently turning back off on navigation. Matches iOS: CrashDetectionView
    // never calls stopMonitoring() from onAppear/onDisappear — only the rider's
    // explicit toggle below does.
    val manager = CrashDetectionManager.get()

    val monitoring by manager.isMonitoring.collectAsState()
    val crashDetected by manager.crashDetected.collectAsState()
    val countdown by manager.countdownSeconds.collectAsState()
    val gForce by manager.currentGForce.collectAsState()
    val maxG by manager.maxGForce.collectAsState()
    val alertToken by manager.emergencyAlertToken.collectAsState()
    val smsTargets by manager.smsTargets.collectAsState()
    val smsBody by manager.smsBody.collectAsState()

    val store = remember { EmergencyContactStore(context) }
    var contacts by remember { mutableStateOf(store.load()) }
    var showAddContact by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var showNoContactsAlert by remember { mutableStateOf(false) }
    var showTextUnavailableAlert by remember { mutableStateOf(false) }
    var showProtectionDisclosure by remember { mutableStateOf(false) }
    var showBackgroundPermissionNotice by remember { mutableStateOf(false) }

    // Same followed-friends list iOS's AddContactSheet uses for the "link a
    // PackRide friend" step (profileManager.listenForFollowedUsers() /
    // stopListeningForFollowedUsers() in CrashDetectionView's onAppear /
    // onDisappear) — FriendsManager is this app's port of that listener.
    val friendsMgr = remember { FriendsManager(context) }
    val followedUsers by friendsMgr.followedUsers.collectAsState()
    DisposableEffect(Unit) {
        friendsMgr.start()
        onDispose { friendsMgr.stop() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* location optional for alert map link */ }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) manager.startMonitoring() else showBackgroundPermissionNotice = true
    }

    fun enableProtectionWithRequiredAccess() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showBackgroundPermissionNotice = true
            return
        }
        val hasBackground = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        when {
            hasBackground -> manager.startMonitoring()
            Build.VERSION.SDK_INT == 29 -> backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> showBackgroundPermissionNotice = true
        }
    }

    LaunchedEffect(Unit) {
        val need = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // Keyed on the monotonic token (not a plain Boolean) so a SECOND crash
    // within one monitoring session — two Run Test Alert taps, or a real
    // crash after a cancelled false alarm — reliably reopens the composer.
    // See the comment on CrashDetectionManager.emergencyAlertToken.
    LaunchedEffect(alertToken) {
        if (alertToken == 0L) return@LaunchedEffect
        if (smsTargets.isEmpty()) {
            showNoContactsAlert = true
            return@LaunchedEffect
        }
        val addresses = smsTargets.joinToString(";")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$addresses")
            putExtra("sms_body", smsBody)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showTextUnavailableAlert = true
        }
    }

    val gForceColor = when {
        gForce < 2 -> Color(0xFF2DA65B)
        gForce < 3 -> Pr.coral
        else -> Color(0xFFD33B2C)
    }

    Box(Modifier.fillMaxSize().background(Pr.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ProtectionHero(
                monitoring = monitoring,
                onToggle = {
                    if (monitoring) manager.stopMonitoring()
                    else showProtectionDisclosure = true
                }
            )

            if (monitoring) {
                LiveMonitorSection(
                    currentG = gForce,
                    maxG = maxG,
                    gForceColor = gForceColor,
                    onRunTest = { manager.simulateCrash() }
                )
            }

            EmergencyContactsSection(
                contacts = contacts,
                onAdd = { editingContact = null; showAddContact = true },
                onEdit = { c -> editingContact = c; showAddContact = true },
                onDelete = { c ->
                    contacts = contacts.filterNot { it.id == c.id }
                    store.save(contacts)
                }
            )

            HowItWorksSection()

            Spacer(Modifier.height(34.dp))
        }

        if (crashDetected) {
            CrashAlertOverlay(
                countdown = countdown,
                onCancel = { manager.cancelAlert() },
                onSendNow = { manager.sendNow() }
            )
        }
    }

    if (showProtectionDisclosure) {
        AlertDialog(
            onDismissRequest = { showProtectionDisclosure = false },
            title = { Text("Turn on crash protection?") },
            text = {
                Text(
                    "PackRide will monitor motion sensors and location while protection is on, including when the screen is off. " +
                        "A persistent notification shows that monitoring is active. If a possible crash is not cancelled within 30 seconds, " +
                        "your location and impact reading are shared only with your linked emergency contacts and active group ride."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showProtectionDisclosure = false
                    enableProtectionWithRequiredAccess()
                }) { Text("Turn On") }
            },
            dismissButton = {
                TextButton(onClick = { showProtectionDisclosure = false }) { Text("Not Now") }
            }
        )
    }

    if (showBackgroundPermissionNotice) {
        AlertDialog(
            onDismissRequest = { showBackgroundPermissionNotice = false },
            title = { Text("Allow background location") },
            text = {
                Text("Crash protection needs location while PackRide is backgrounded or the screen is locked. In Permissions → Location, choose Allow all the time, then return and turn protection on.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundPermissionNotice = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundPermissionNotice = false }) { Text("Not Now") }
            }
        )
    }

    if (showAddContact) {
        AddContactDialog(
            existing = editingContact,
            followedUsers = followedUsers,
            onSave = { contact ->
                contacts = if (contacts.any { it.id == contact.id }) {
                    contacts.map { if (it.id == contact.id) contact else it }
                } else {
                    contacts + contact
                }
                store.save(contacts)
                showAddContact = false
            },
            onCancel = { showAddContact = false }
        )
    }

    if (showNoContactsAlert) {
        AlertDialog(
            onDismissRequest = { showNoContactsAlert = false },
            title = { Text("No Emergency Contacts") },
            text = { Text("Add at least one emergency contact before relying on crash alerts.") },
            confirmButton = {
                TextButton(onClick = { showNoContactsAlert = false }) { Text("OK") }
            }
        )
    }

    if (showTextUnavailableAlert) {
        AlertDialog(
            onDismissRequest = { showTextUnavailableAlert = false },
            title = { Text("Text Message Unavailable") },
            text = { Text("This device is not configured to send text messages.") },
            confirmButton = {
                TextButton(onClick = { showTextUnavailableAlert = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ProtectionHero(monitoring: Boolean, onToggle: () -> Unit) {
    val greenBright = Color(0xFF5FD98A)
    val redBright = Color(0xFFD33B2C)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Pr.inkFixed, Pr.bg)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 54.dp, bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "SAFETY",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Pr.coral)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Crash Detection",
                        style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (monitoring) greenBright.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = if (monitoring) greenBright else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                if (monitoring) "Protection is active" else "Protection is currently off",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (monitoring) greenBright else Color.White.copy(alpha = 0.62f)),
                modifier = Modifier.padding(top = 18.dp)
            )

            Text(
                if (monitoring)
                    "PackRide is monitoring impact forces while you ride."
                else
                    "Turn protection on before you head out. PackRide will monitor impact forces in the background.",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.72f), lineHeight = 19.sp),
                modifier = Modifier.padding(top = 6.dp)
            )

            Box(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (monitoring) redBright else Pr.coral)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (monitoring) Icons.Filled.Close else Icons.Filled.Shield,
                        contentDescription = null,
                        tint = if (monitoring) Color.White else Pr.inkFixed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (monitoring) "Disable Protection" else "Enable Protection",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (monitoring) Color.White else Pr.inkFixed)
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (monitoring) Color.White else Pr.inkFixed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSectionLabel(title: String, trailing: String? = null, trailingColor: Color = Pr.coral) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Pr.muted))
        if (trailing != null) {
            Text(trailing, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = trailingColor))
        }
    }
}

@Composable
private fun LiveMonitorSection(currentG: Double, maxG: Double, gForceColor: Color, onRunTest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pr.inkFixed)
            .border(0.dp, Color.Transparent)
    ) {
        WebSectionLabel(title = "LIVE MONITOR", trailing = "4.0G THRESHOLD")

        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                CrashMetric(
                    value = "%.1fG".format(currentG),
                    label = "Current",
                    tint = gForceColor,
                    modifier = Modifier.weight(1f)
                )
                Box(Modifier.width(1.dp).height(54.dp).background(Pr.border))
                CrashMetric(
                    value = "%.1fG".format(maxG),
                    label = "Max",
                    tint = Pr.coral,
                    modifier = Modifier.weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                )
                val gaugeRatio = (currentG.coerceIn(0.0, 6.0) / 6.0).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(gaugeRatio)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(gForceColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(4f / 6f)
                        .height(18.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(Modifier.width(2.dp).height(18.dp).background(Pr.coral))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRunTest)
                    .padding(top = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run Test Alert", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pr.coral))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun CrashMetric(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Box(Modifier.width(3.dp).height(46.dp).background(tint))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(value, style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White))
            Spacer(Modifier.height(4.dp))
            Text(label, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.52f)))
        }
    }
}

@Composable
private fun EmergencyContactsSection(
    contacts: List<EmergencyContact>,
    onAdd: () -> Unit,
    onEdit: (EmergencyContact) -> Unit,
    onDelete: (EmergencyContact) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WebSectionLabel(title = "EMERGENCY NETWORK", trailing = "${contacts.size}/${EmergencyContactStore.MAX}")

        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            if (contacts.isEmpty()) {
                CrashContactRow(label = "Emergency Contact", value = "", onTap = onAdd, onDelete = null)
            } else {
                contacts.forEachIndexed { index, contact ->
                    if (index > 0) CrashDivider()
                    CrashContactRow(
                        label = if (index == 0) "Primary Contact" else "Contact ${index + 1}",
                        value = "${contact.name} — ${contact.phone}",
                        onTap = { onEdit(contact) },
                        onDelete = { onDelete(contact) }
                    )
                }
                if (contacts.size < EmergencyContactStore.MAX) {
                    CrashDivider()
                    CrashContactRow(label = "Add Contact", value = "", onTap = onAdd, onDelete = null)
                }
            }
        }
    }
}

@Composable
private fun CrashContactRow(label: String, value: String, onTap: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (value.isEmpty()) Pr.coralSoft else Color(0xFF2DA65B).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (value.isEmpty()) Icons.Filled.Add else Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (value.isEmpty()) Pr.coral else Color(0xFF2DA65B),
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(label.uppercase(), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Pr.muted))
                Spacer(Modifier.height(3.dp))
                Text(
                    value.ifEmpty { "Add an emergency contact" },
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (value.isEmpty()) Pr.muted else Pr.ink),
                    maxLines = 1
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted.copy(alpha = 0.65f), modifier = Modifier.size(16.dp))
        }

        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove contact", tint = Color(0xFFD33B2C), modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun CrashDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(1.dp)
            .background(Pr.border)
    )
}

@Composable
private fun HowItWorksSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        WebSectionLabel(title = "HOW IT WORKS", trailing = "AUTOMATIC")

        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            CrashStep("01", "Monitor", "The app watches G-force from your phone's accelerometer while protection is active.")
            CrashDivider()
            CrashStep("02", "Detect", "An impact above 4G starts a 30-second safety countdown.")
            CrashDivider()
            CrashStep("03", "Confirm", "Cancel the alert when you're okay, or send immediately when you need help.")
            CrashDivider()
            CrashStep("04", "Notify", "Your emergency contacts receive a message with your latest available GPS location.")
        }
    }
}

@Composable
private fun CrashStep(number: String, title: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            number,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = Pr.coral),
            modifier = Modifier.width(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink))
            Spacer(Modifier.height(4.dp))
            Text(text, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Pr.muted, lineHeight = 16.sp))
        }
    }
}

@Composable
private fun CrashAlertOverlay(countdown: Int, onCancel: () -> Unit, onSendNow: () -> Unit) {
    // Aug 24, 2026-equivalent fix carried over from iOS: this backdrop is a
    // literal fixed dark color, not an adaptive Pr.ink/Pr.bg token — it's
    // meant to stay a near-black emergency overlay regardless of the app's
    // light/dark theme setting.
    val fixedDark = Color(0xFF131110)
    val warnRed = Color(0xFFFF5A45)
    val warnRedDim = Color(0xFFD33B2C)
    val okGreen = Color(0xFF5FD98A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fixedDark.copy(alpha = 0.97f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(warnRedDim.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = warnRed, modifier = Modifier.size(56.dp))
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Crash Detected!",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Are you okay? Emergency text\nopens in $countdown seconds.",
                style = TextStyle(fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f)),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(100.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 8.dp
                )
                CircularProgressIndicator(
                    progress = { countdown / 30f },
                    modifier = Modifier.size(100.dp),
                    color = warnRed,
                    strokeWidth = 8.dp
                )
                Text("$countdown", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White))
            }

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(okGreen)
                        .clickable(onClick = onCancel)
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I'm Okay — Cancel Alert", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1A17)))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(warnRed.copy(alpha = 0.12f))
                        .clickable(onClick = onSendNow)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Send Alert Now", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = warnRed))
                }
            }
        }
    }
}

@Composable
private fun AddContactDialog(
    existing: EmergencyContact?,
    followedUsers: List<RiderProfile>,
    onSave: (EmergencyContact) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var relationship by remember { mutableStateOf(existing?.relationship ?: "") }
    var linkedUserID by remember { mutableStateOf(existing?.linkedUserID) }

    Dialog(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Pr.RadiusSheet))
                .background(Pr.bg)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    if (existing == null) "Add Emergency Contact" else "Edit Contact",
                    style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(14.dp))

                Text("RELATIONSHIP", style = PrFont.micro)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(relationships) { rel ->
                        RelationshipChip(rel, selected = relationship == rel, onClick = { relationship = rel })
                    }
                }

                if (followedUsers.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("LINK A PACKRIDE FRIEND (OPTIONAL)", style = PrFont.micro)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        followedUsers.take(8).forEach { friend ->
                            val isLinked = linkedUserID == friend.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Pr.RadiusButton))
                                    .background(if (isLinked) Pr.coralSoft else Pr.fieldBg)
                                    .clickable { linkedUserID = if (isLinked) null else friend.id }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    friend.name,
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink),
                                    modifier = Modifier.weight(1f)
                                )
                                if (isLinked) {
                                    Text("LINKED", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = Pr.coral))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Pr.RadiusButton))
                            .background(Pr.fieldBg)
                            .clickable(onClick = onCancel)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Pr.RadiusButton))
                            .background(if (name.isBlank()) Pr.disabledGradient else Pr.accentGradient)
                            .clickable(enabled = name.isNotBlank()) {
                                onSave(
                                    EmergencyContact(
                                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        phone = phone.trim(),
                                        relationship = relationship,
                                        linkedUserID = linkedUserID
                                    )
                                )
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pr.coral else Pr.fieldBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            title,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Pr.ink)
        )
    }
}
