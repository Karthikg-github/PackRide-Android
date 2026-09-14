package com.karthik.packride.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.community.Community
import com.karthik.packride.community.CommunityManager
import com.karthik.packride.community.CommunityMember
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.community.PendingCommunityJoin
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.help.HelpRequestManager
import com.karthik.packride.group.PendingGroupRide
import com.karthik.packride.moderation.ModerationManager
import com.karthik.packride.schedule.ScheduledRideManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrimaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Aug 30, 2026 — Community visual+functionality parity pass. Port of iOS's
// CommunityView.swift (1163 lines; Android was a 107-line stub showing a
// public directory of every community with no-passcode "Join," plus an
// unrelated "all app users" browse tab). See CommunityManager.kt's file
// header for why the old model was a real gap, not just a lighter one.
//
// Structural note: iOS pushes CommunityDetailView via NavigationLink from
// CommunityListView. MoreScreen (this screen's host) is a flat tab strip
// with no navController — the same limitation already flagged for Garage's
// back button, Ride History's quick-links, and Profile's notification bell.
// Handled here with in-composable state (selectedCommunityId) instead of
// real navigation: tapping a community card switches this screen into
// "detail mode" with its own back button, rather than pushing a new route.
//
// Aug 30, 2026 — packride://joincommunity?id=XXXX&passcode=YYYY added,
// closing the gap flagged below in the original pass. MainActivity now
// parses the incoming Intent's data Uri (it previously didn't parse ANY
// deep link at all, despite the manifest's intent-filter and the existing
// packride://join?code=XXXX share text already implying it worked — that
// receiving half was missing too and got added alongside this one; see
// MainActivity.handleDeepLink) into PendingCommunityJoin, PackRideNav
// switches to the More tab, MoreScreen selects the Community sub-tab, and
// this screen's own LaunchedEffect below prefills (not auto-submits) Join
// Community — same "prefill, still requires a tap" shape as the group ride
// join link and as iOS's CommunityListView.onAppear.
//
// Deliberately skipped this pass, flagged rather than faked:
// - Live map of active riders (CommunityMapView) — activeRiders is real
//   plumbing (see CommunityManager.kt) but, matching iOS's own current
//   behavior, nothing populates isRiding=true during an actual ride on
//   either platform yet, so a map here would almost always be empty.
// - CommunityScheduledRidesSection (an embedded per-community schedule
//   widget) — a genuinely separate feature; Android's ScheduleRideScreen
//   exists standalone but has no community-scoped embedded view.
// - Follow button is two-state (Follow/Following) rather than iOS's three-
//   state (Follow/Sent/Following) — same FriendsManager gap already flagged
//   in the Profile pass: Android's follow model has no request/approval
//   concept, only direct follow/unfollow.
@Composable
fun CommunityScreen(auth: AuthManager? = null) {
    val context = LocalContext.current
    val riderName = auth?.prefsSnapshot?.riderName?.takeIf { it.isNotBlank() } ?: "Rider"
    val store = remember { CommunityMembershipStore.get() }
    val myCommunities by store.myCommunities.collectAsState()

    var selectedCommunityId by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }
    var joinPrefillId by remember { mutableStateOf("") }
    var joinPrefillPasscode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { store.pruneDeletedCommunities() }
    // Consumes a tapped packride://joincommunity link (see PendingCommunityJoin.kt
    // / MainActivity.handleDeepLink) — prefills Join Community and opens it,
    // but still requires the person to tap Join themselves, same as the
    // group ride join link.
    LaunchedEffect(Unit) {
        val pending = PendingCommunityJoin.pending.value ?: return@LaunchedEffect
        PendingCommunityJoin.clear()
        joinPrefillId = pending.id
        joinPrefillPasscode = pending.passcode
        showJoinSheet = true
    }

    val selected = myCommunities.firstOrNull { it.id == selectedCommunityId }
    if (selected != null) {
        CommunityDetailScreen(
            community = selected,
            riderName = riderName,
            onBack = { selectedCommunityId = null },
            onLeftOrDeleted = { selectedCommunityId = null }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        PrPageHeader(
            eyebrow = "The Pack",
            title = "My Communities",
            subtitle = if (myCommunities.isEmpty()) "Find your riding crew"
            else "${myCommunities.size} communit${if (myCommunities.size == 1) "y" else "ies"}"
        )

        if (myCommunities.isEmpty()) {
            CommunityWelcome(
                onCreate = { showCreateSheet = true },
                onJoin = { showJoinSheet = true }
            )
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showCreateSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Pr.coral)
                        Spacer(Modifier.width(4.dp))
                        Text("Create", color = Pr.coral)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    myCommunities.forEach { c ->
                        CommunityListCard(community = c, onClick = { selectedCommunityId = c.id })
                    }
                    TextButton(
                        onClick = { showJoinSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Join Another Community", color = Pr.coral) }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateCommunityDialog(
            store = store,
            riderName = riderName,
            onDismiss = { showCreateSheet = false },
            onCreated = { id -> showCreateSheet = false; selectedCommunityId = id }
        )
    }
    if (showJoinSheet) {
        JoinCommunityDialog(
            store = store,
            riderName = riderName,
            initialId = joinPrefillId,
            initialPasscode = joinPrefillPasscode,
            onDismiss = { showJoinSheet = false },
            onJoined = { id -> showJoinSheet = false; selectedCommunityId = id }
        )
    }
}

@Composable
private fun CommunityWelcome(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(Pr.coralSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Your Riding Tribe", style = PrFont.title)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create or join a community to ride together and stay safe.",
            style = PrFont.caption
        )
        Spacer(Modifier.height(24.dp))
        PrCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CommunityFeatureRow(text = "See community riders live during solo rides")
                CommunityFeatureRow(text = "Get notified if a community rider crashes")
                CommunityFeatureRow(text = "Instant alerts when someone needs help")
                CommunityFeatureRow(text = "Private — join only with a passcode")
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "Create a Community", onClick = onCreate, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
            Text("Join a Community", color = Pr.coral)
        }
    }
}

@Composable
private fun CommunityFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Pr.coral))
        Text(text, style = PrFont.bodySmall)
    }
}

@Composable
private fun CommunityListCard(community: Community, onClick: () -> Unit) {
    PrCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(Pr.coral),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(community.name, style = PrFont.subheading)
                Text("ID: ${community.id}", style = PrFont.caption)
            }
        }
    }
}

@Composable
private fun CreateCommunityDialog(
    store: CommunityMembershipStore,
    riderName: String,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    val isLoading by store.isLoading.collectAsState()
    val errorMessage by store.errorMessage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Community") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Community name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(passcode, { passcode = it }, label = { Text("Passcode") }, modifier = Modifier.fillMaxWidth())
                Text("Share this passcode with riders you want to invite.", style = PrFont.caption)
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = PrFont.caption)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    store.createCommunity(name, passcode, riderName) { id ->
                        if (id != null) onCreated(id)
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun JoinCommunityDialog(
    store: CommunityMembershipStore,
    riderName: String,
    initialId: String = "",
    initialPasscode: String = "",
    onDismiss: () -> Unit,
    onJoined: (String) -> Unit
) {
    var id by remember { mutableStateOf(initialId) }
    var passcode by remember { mutableStateOf(initialPasscode) }
    val isLoading by store.isLoading.collectAsState()
    val errorMessage by store.errorMessage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Community") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ask your ride leader for the Community ID and Passcode.", style = PrFont.caption)
                OutlinedTextField(id, { id = it }, label = { Text("Community ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(passcode, { passcode = it }, label = { Text("Passcode") }, modifier = Modifier.fillMaxWidth())
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = PrFont.caption)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    store.joinCommunity(id, passcode, riderName) { joinedId ->
                        if (joinedId != null) onJoined(joinedId)
                    }
                }
            ) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CommunityDetailScreen(
    community: Community,
    riderName: String,
    onBack: () -> Unit,
    onLeftOrDeleted: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { CommunityMembershipStore.get() }
    val manager = remember(community.id) { CommunityManager(context, community.id) }
    val myCommunity by manager.myCommunity.collectAsState()
    val members by manager.members.collectAsState()
    val activeRiders by manager.activeRiders.collectAsState()
    val wasDeleted by manager.wasDeleted.collectAsState()

    val friendsMgr = remember { FriendsManager(context) }
    val followedIds = friendsMgr.followedUsers.collectAsState().value.map { it.id }.toSet()
    val moderation = remember { ModerationManager(context) }
    val blockedIds = moderation.blockedRiders.collectAsState().value.map { it.id }.toSet()
    val scheduleManager = remember(community.id) { ScheduledRideManager(context) }
    val communityRides by scheduleManager.communityRides.collectAsState()

    val helpMgr = remember { HelpRequestManager.get() }
    val activeHelpRequests by helpMgr.activeRequests.collectAsState()
    val communityHelpRequests = activeHelpRequests.filter { it.targetType == "community" && it.targetID == community.id }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reportMember by remember { mutableStateOf<CommunityMember?>(null) }
    var blockMember by remember { mutableStateOf<CommunityMember?>(null) }
    var moderationMessage by remember { mutableStateOf<String?>(null) }

    val current = myCommunity ?: community
    val isCreator = manager.isCreator

    LaunchedEffect(community.id) { manager.start(community) }
    DisposableEffect(community.id) { onDispose { manager.stop() } }
    LaunchedEffect(Unit) { friendsMgr.start() }
    LaunchedEffect(Unit) { moderation.start() }
    LaunchedEffect(community.id) { scheduleManager.listenForCommunityRides(community.id) }
    DisposableEffect(Unit) { onDispose { friendsMgr.stop(); moderation.stop(); scheduleManager.stop() } }
    LaunchedEffect(Unit) { helpMgr.listenForActiveRequests() }
    DisposableEffect(Unit) { onDispose { helpMgr.stopListening() } }
    LaunchedEffect(wasDeleted) {
        if (wasDeleted) {
            store.remove(community.id)
            onLeftOrDeleted()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Pr.ink,
                modifier = Modifier.clickable(onClick = onBack)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(current.name, style = PrFont.heading)
                Text("${members.size + 1} member${if (members.isEmpty()) "" else "s"}", style = PrFont.caption)
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (communityHelpRequests.isNotEmpty()) {
                val first = communityHelpRequests.first()
                val label = if (communityHelpRequests.size > 1)
                    "${first.requesterName} and ${communityHelpRequests.size - 1} other${if (communityHelpRequests.size > 2) "s" else ""} need help"
                else "${first.requesterName} needs help"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(label, color = androidx.compose.ui.graphics.Color.White, style = PrFont.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
            }

            PrCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape).background(Pr.coral),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.name, style = PrFont.subheading)
                            Text("ID: ${current.id}", style = PrFont.caption)
                        }
                        if (isCreator) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete community",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable { showDeleteConfirm = true }.padding(end = 8.dp)
                            )
                        }
                        TextButton(onClick = { showLeaveConfirm = true }) { Text("Leave", color = Pr.muted) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Passcode", style = PrFont.caption)
                            Text(current.passcode, style = PrFont.subheading)
                        }
                        TextButton(onClick = {
                            val joinURL = "packride://joincommunity?id=${current.id}&passcode=${current.passcode}"
                            val text = "Join ${current.name} on PackRide!\n" +
                                "Community ID: ${current.id}\nPasscode: ${current.passcode}\n\n" +
                                "Tap to join instantly (if you already have PackRide): $joinURL"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Invite to ${current.name}"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = Pr.coral)
                            Spacer(Modifier.width(6.dp))
                            Text("Invite", color = Pr.coral)
                        }
                    }

                    if (activeRiders.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Riding Now", style = PrFont.subheading)
                                Text("${activeRiders.size} active", style = PrFont.caption)
                            }
                            activeRiders.forEach { rider -> ActiveRiderRow(rider) }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Scheduled Rides", style = PrFont.subheading)
                            if (communityRides.isNotEmpty()) Text("${communityRides.size}", style = PrFont.caption, color = Pr.teal)
                        }
                        if (communityRides.isEmpty()) {
                            Text("No upcoming rides — schedule one and share it with this community.", style = PrFont.caption)
                        } else {
                            val formatter = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
                            communityRides.take(5).forEach { ride ->
                                Row(
                                    Modifier.fillMaxWidth().background(Pr.fieldBg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                        .clickable { PendingGroupRide.request(ride.rideCode, ride.creatorID == scheduleManager.myID) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CalendarMonth, null, tint = Pr.teal)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ride.title, style = PrFont.body)
                                        Text(formatter.format(Date((ride.scheduledDate * 1000).toLong())), style = PrFont.caption)
                                    }
                                    Text("${ride.rsvpCount} going", style = PrFont.caption)
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Members", style = PrFont.subheading)
                            Text("${members.size + 1} total", style = PrFont.caption)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().background(Pr.fieldBg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PrAvatar(initials = riderName.take(2).uppercase(), size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text("$riderName (You)", style = PrFont.body)
                                Text(if (isCreator) "Creator" else "Member", style = PrFont.caption)
                            }
                        }

                        if (members.isEmpty()) {
                            Text("No members yet — invite your riding friends!", style = PrFont.caption)
                        } else {
                            members.filter { it.authUID !in blockedIds && it.id !in blockedIds }.forEach { m ->
                                MemberRow(
                                    member = m,
                                    needsHelp = communityHelpRequests.any { it.requesterDeviceID == m.id },
                                    isFollowing = followedIds.contains(m.authUID),
                                    onFollow = {
                                        friendsMgr.sendFollowRequest(
                                            m.authUID,
                                            riderName,
                                            riderName.rideInitials()
                                        )
                                    },
                                    onReport = { reportMember = m },
                                    onBlock = { blockMember = m }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Community?") },
            text = { Text("You will no longer be visible to community members during rides.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    manager.leaveCommunity()
                    store.remove(community.id)
                    onLeftOrDeleted()
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Community?") },
            text = { Text("This permanently deletes the community for every member, not just you. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    manager.deleteCommunity()
                    store.remove(community.id)
                    onLeftOrDeleted()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    reportMember?.let { member ->
        CommunityReportDialog(
            name = member.name,
            onDismiss = { reportMember = null },
            onReason = { reason ->
                reportMember = null
                moderation.report("communityMember", community.id, member.authUID.ifEmpty { member.id }, reason) {
                    moderationMessage = it ?: "Report sent. Thank you for helping keep PackRide safe."
                }
            }
        )
    }
    blockMember?.let { member ->
        AlertDialog(
            onDismissRequest = { blockMember = null },
            title = { Text("Block ${member.name}?") },
            text = { Text("Their community activity and feed posts will be hidden from you.") },
            confirmButton = { TextButton(onClick = {
                blockMember = null
                moderation.block(member.authUID.ifEmpty { member.id }, member.name) {
                    moderationMessage = it ?: "${member.name} blocked"
                }
            }) { Text("Block", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { blockMember = null }) { Text("Cancel") } }
        )
    }
    moderationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { moderationMessage = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { moderationMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun MemberRow(
    member: CommunityMember,
    needsHelp: Boolean,
    isFollowing: Boolean,
    onFollow: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().background(Pr.fieldBg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrAvatar(initials = member.initials, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(member.name, style = PrFont.body)
            Text(
                if (needsHelp) "Needs help" else if (member.isRiding) "Riding now" else "Offline",
                style = PrFont.caption
            )
        }
        if (member.authUID.isNotEmpty()) {
            if (isFollowing) {
                Text("Following", style = PrFont.caption, color = Pr.coral)
            } else {
                TextButton(onClick = onFollow) { Text("Follow", color = Pr.coral) }
            }
            Box {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Member options",
                    tint = Pr.muted,
                    modifier = Modifier.clickable { showMenu = true }.padding(4.dp)
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Report rider") },
                        leadingIcon = { Icon(Icons.Default.Flag, null) },
                        onClick = { showMenu = false; onReport() }
                    )
                    DropdownMenuItem(
                        text = { Text("Block rider") },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                        onClick = { showMenu = false; onBlock() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityReportDialog(name: String, onDismiss: () -> Unit, onReason: (String) -> Unit) {
    val reasons = listOf("Harassment or bullying", "Hate or abuse", "Dangerous content", "Spam or scam", "Privacy or safety", "Other")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $name") },
        text = { Column { reasons.forEach { reason ->
            TextButton(onClick = { onReason(reason) }, modifier = Modifier.fillMaxWidth()) { Text(reason, modifier = Modifier.fillMaxWidth()) }
        } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ActiveRiderRow(rider: CommunityMember) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Pr.fieldBg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrAvatar(initials = rider.initials, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(rider.name, style = PrFont.body)
            Text(com.karthik.packride.data.MeasurementUnits.speedMph(rider.speed), style = PrFont.caption)
        }
        Text("Live", style = PrFont.caption, color = Pr.coral)
    }
}
