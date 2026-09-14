package com.karthik.packride.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.friends.RiderProfile
import com.karthik.packride.group.ActiveRideTracker
import com.karthik.packride.help.HelpRequest
import com.karthik.packride.help.HelpRequestManager
import com.karthik.packride.help.relevantTo
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar
import com.karthik.packride.ui.theme.PrFont

// Aug 31, 2026 — visual restyle onto the Pr design system, matching this
// screen's sibling tab CrashDetectionScreen.kt (same SafetyHubScreen
// 2-tab switcher) so the two Safety tabs read as one coherent hub rather
// than two different apps stitched together: same dark-gradient hero
// treatment, same hand-rolled Box+clickable rows/dividers instead of
// stock Material Button/Card, same uppercase section-label convention,
// same 42dp icon-circle + 72dp divider-inset row rhythm. All Firebase
// listening, targeting, and relevantTo() filtering below is untouched —
// this pass only changes how it's drawn.

private val helpRedBright = Color(0xFFFF5A45)
private val helpRedDim = Color(0xFFD33B2C)
private val helpGreenBright = Color(0xFF5FD98A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedHelpScreen(auth: AuthManager) {
    val context = LocalContext.current
    // Aug 30, 2026 — Need Help targeting fix: manager is now the app-wide
    // singleton (see HelpRequestManager.kt) rather than a fresh per-screen
    // instance, so a share started here keeps running after leaving this
    // screen and the app-wide banner (PackRideNav) sees the same listener.
    val manager = remember { HelpRequestManager.get() }
    val locationManager = remember { SharedLocationManager.get() }
    val friendsManager = remember { FriendsManager(context) }

    val sharing by manager.isSharing.collectAsState()
    val label by manager.sharingWithLabel.collectAsState()
    val requests by manager.activeRequests.collectAsState()
    val error by manager.error.collectAsState()
    val myLoc by locationManager.location.collectAsState()
    val followedUsers by friendsManager.followedUsers.collectAsState()
    val myCommunities by CommunityMembershipStore.get().myCommunities.collectAsState()
    val activeRideCode by ActiveRideTracker.get().activeRideCode.collectAsState()

    var showFriendPicker by remember { mutableStateOf(false) }
    var showBackgroundPermissionNotice by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun ensurePermissions(): Boolean {
        val need = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return false
        }
        return true
    }

    LaunchedEffect(Unit) {
        ensurePermissions()
        locationManager.startUpdating(SharedLocationManager.REASON_MAP)
        manager.listenForActiveRequests()
        friendsManager.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            locationManager.stopUpdating(SharedLocationManager.REASON_MAP)
            manager.stopListening()
            friendsManager.stop()
            // Do NOT stop sharing on leave — share continues in background like iOS
        }
    }

    val name = auth.prefsSnapshot.riderName
    val initials = name.rideInitials()

    fun startSharing(targetType: String, targetID: String, targetName: String) {
        if (!ensurePermissions()) return
        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showBackgroundPermissionNotice = true
            return
        }
        val loc = myLoc
        if (loc == null) {
            locationManager.startUpdating(SharedLocationManager.REASON_MAP)
            return
        }
        manager.start(
            targetType = targetType,
            targetID = targetID,
            targetName = targetName,
            requesterName = name,
            requesterInitials = initials,
            latitude = loc.latitude,
            longitude = loc.longitude
        )
    }

    if (showBackgroundPermissionNotice) {
        AlertDialog(
            onDismissRequest = { showBackgroundPermissionNotice = false },
            title = { Text("Allow background location") },
            text = { Text("Need Help keeps sharing your live location when PackRide is backgrounded or the screen is locked. In Permissions → Location, choose Allow all the time, then return and start sharing again.") },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundPermissionNotice = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }) { Text("Open Settings") }
            },
            dismissButton = { TextButton(onClick = { showBackgroundPermissionNotice = false }) { Text("Not Now") } }
        )
    }

    val myCommunityIds = myCommunities.map { it.id }.toSet()
    val others = requests.relevantTo(manager.myUID, manager.myDeviceID, myCommunityIds, activeRideCode)

    Box(Modifier.fillMaxSize().background(Pr.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            NeedHelpHero(sharing = sharing, label = label, onStop = { manager.stop() })

            error?.let {
                Text(
                    it,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = helpRedDim),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)
                )
            }

            if (!sharing) {
                NeedHelpWebSectionLabel(title = "SHARE YOUR LOCATION")

                Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
                    ShareTargetRow(
                        icon = Icons.Filled.Person,
                        label = "Share with a Friend",
                        enabled = followedUsers.isNotEmpty(),
                        onClick = { showFriendPicker = true }
                    )

                    myCommunities.forEach { community ->
                        NeedHelpDivider()
                        ShareTargetRow(
                            icon = Icons.Filled.Groups,
                            label = "Share with ${community.name}",
                            enabled = true,
                            onClick = { startSharing("community", community.id, community.name) }
                        )
                    }

                    if (activeRideCode.isNotEmpty()) {
                        NeedHelpDivider()
                        ShareTargetRow(
                            icon = Icons.Filled.Group,
                            label = "Share with Group Ride",
                            enabled = true,
                            onClick = { startSharing("group", activeRideCode, "your Group Ride") }
                        )
                    }
                }

                if (followedUsers.isEmpty() && myCommunities.isEmpty() && activeRideCode.isEmpty()) {
                    Text(
                        "You're not following anyone, in a community, or in a group ride yet — " +
                            "follow a friend or join one from the Community tab so there's someone to alert.",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pr.muted, lineHeight = 16.sp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }

            // Aug 30, 2026 — Need Help targeting fix: was `requests.filter { it.requesterUID != auth.uid }`,
            // showing every active request in Firebase regardless of who it was
            // actually shared with. Now uses the same relevantTo() filter the
            // app-wide banner (PackRideNav) uses, so a request can only reach the
            // specific friend/community/group it targeted.
            NeedHelpWebSectionLabel(title = "ACTIVE HELP REQUESTS NEARBY", trailing = if (others.isNotEmpty()) "${others.size}" else null)

            if (others.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Pr.muted.copy(alpha = 0.45f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No active requests right now.",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Pr.muted)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
                    others.forEachIndexed { index, req ->
                        if (index > 0) NeedHelpDivider()
                        HelpRequestRow(request = req, context = context)
                    }
                }
            }

            Spacer(Modifier.height(34.dp))
        }
    }

    if (showFriendPicker) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showFriendPicker = false },
            sheetState = sheetState
        ) {
            FriendPickerContent(friends = followedUsers) { friend ->
                showFriendPicker = false
                startSharing("friend", friend.id, friend.name)
            }
        }
    }
}

/** Dark-gradient status hero — mirrors CrashDetectionScreen's ProtectionHero so the two Safety tabs match. */
@Composable
private fun NeedHelpHero(sharing: Boolean, label: String, onStop: () -> Unit) {
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
                        "Need Help",
                        style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (sharing) helpGreenBright.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = if (sharing) helpGreenBright else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                if (sharing) "Sharing your live location" else "Not currently sharing",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sharing) helpGreenBright else Color.White.copy(alpha = 0.62f)),
                modifier = Modifier.padding(top = 18.dp)
            )

            Text(
                if (sharing)
                    "with $label — your location keeps updating automatically until you stop."
                else
                    "Share your live location with a friend, your community, or your group ride so someone you trust can find you fast.",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.72f), lineHeight = 19.sp),
                modifier = Modifier.padding(top = 6.dp)
            )

            if (sharing) {
                Box(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(helpRedDim)
                        .clickable(onClick = onStop)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Stop Sharing",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                }
            }
        }
    }
}

/** Uppercase section label with optional trailing detail — mirrors CrashDetectionScreen's private WebSectionLabel. */
@Composable
private fun NeedHelpWebSectionLabel(title: String, trailing: String? = null) {
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
            Text(trailing, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = Pr.coral))
        }
    }
}

/** Hairline row divider, inset to clear the 42dp icon circle — mirrors CrashDetectionScreen's CrashDivider. */
@Composable
private fun NeedHelpDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(1.dp)
            .background(Pr.border)
    )
}

@Composable
private fun ShareTargetRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (enabled) Pr.coralSoft else Pr.fieldBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Pr.coral else Pr.muted,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                label,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Pr.ink else Pr.muted)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Pr.muted.copy(alpha = 0.65f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun HelpRequestRow(request: HelpRequest, context: Context) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrAvatar(initials = request.requesterInitials, size = 42.dp)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        request.requesterName,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
                    )
                    Spacer(Modifier.width(8.dp))
                    NeedsHelpBadge()
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Shared with ${request.targetName}",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pr.muted)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelpActionChip(icon = Icons.Filled.Map, label = "Open Map") {
                val uri = Uri.parse(
                    "geo:${request.latitude},${request.longitude}?q=${request.latitude},${request.longitude}(${Uri.encode(request.requesterName)})"
                )
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            HelpActionChip(icon = Icons.Filled.LocationOn, label = "Google Maps") {
                val uri = Uri.parse("https://maps.google.com/?q=${request.latitude},${request.longitude}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }
}

@Composable
private fun HelpActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Pr.fieldBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink))
    }
}

/** Small reusable pill — port of iOS's NeedsHelpBadge. */
@Composable
private fun NeedsHelpBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(helpRedBright)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            "NEEDS HELP",
            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = Color.White)
        )
    }
}

@Composable
private fun FriendPickerContent(friends: List<RiderProfile>, onPick: (RiderProfile) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Share With a Friend", style = PrFont.heading)
        LazyColumn(
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(friends, key = { it.id }) { friend ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Pr.RadiusButton))
                        .clickable { onPick(friend) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrAvatar(initials = friend.initials, size = 36.dp, photoUrl = friend.avatarURL)
                    Text(
                        friend.name,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink),
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Pr.muted.copy(alpha = 0.65f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
