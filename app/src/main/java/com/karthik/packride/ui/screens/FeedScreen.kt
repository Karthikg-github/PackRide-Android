package com.karthik.packride.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.ads.AdBannerFooter
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.feed.FeedComment
import com.karthik.packride.feed.FeedPost
import com.karthik.packride.feed.FeedRoutePoint
import com.karthik.packride.feed.RideFeedManager
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.friends.RiderProfile
import com.karthik.packride.lap.LapEngine
import com.karthik.packride.moderation.ModerationManager
import com.karthik.packride.share.RideShareCard
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar
import com.karthik.packride.ui.theme.PrCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Aug 30, 2026 — full port of iOS RideFeedView (was a 207-line stub: a plain
// LazyColumn of Card()s with no design-system styling, no Suggested Friends,
// no route preview, no reaction picker, no comment moderation). The manager
// (RideFeedManager.kt) already had the real Firebase plumbing from the
// RideHistory "Post to Feed" pass — this rewrite is mostly UI, plus two real
// gaps closed on the manager: myKnownIDs (ownership survives an auth-state
// change, matching iOS) and deleteComment (comment moderation didn't exist
// on Android at all before this).
//
// Aug 31, 2026 — photo rendering closed: FeedPostCard now shows post.photoURL
// via Coil's AsyncImage (240dp, cropped, matching iOS's frame) when a post has
// one, falling back to the route map exactly like iOS's own if/else-if — see
// RideFeedView.swift. The photo itself is attached from RideHistoryScreen's
// "Post to Feed" dialog and uploaded via ImageCloudUpload to
// feedPhotos/{postId}.jpg, the same Storage path iOS's RideFeedManager.postRide
// writes to, so a photo posted from either platform renders on both.
//
// Deliberately NOT ported, and why:
//  - "Recommended Ride" card. It's defined by iOS as "the most-liked post
//    that has a photo" and lives on iOS's Home tab, not the feed screen
//    itself — a separate surface this pass didn't touch; out of scope for
//    the Profile/Feed photo-upload gap this closes.
//  - The notification bell / follow-request badge. Same reason HomeScreen.kt
//    skipped it: FriendsManager has no followRequests concept on Android,
//    follow is instant. A manual Refresh icon takes its place in the header.
//  - Pull-to-refresh gesture. No pull-refresh/accompanist dependency exists
//    in this project yet; the feed is a live RTDB listener anyway (it
//    updates on its own), so the Refresh icon is a deliberate, simpler
//    equivalent rather than a real gap.

private enum class FeedMode { Feed, Mine, MyLaps }

@Composable
fun FeedScreen(auth: AuthManager) {
    val context = LocalContext.current
    val feed = remember { RideFeedManager(context) }
    val friends = remember { FriendsManager(context) }
    val moderation = remember { ModerationManager(context) }
    val posts by feed.posts.collectAsState()
    val loading by feed.isLoading.collectAsState()
    val following by friends.followedUsers.collectAsState()
    val discover by friends.discover.collectAsState()
    val requestedIds by friends.requestedIds.collectAsState()
    val error by feed.error.collectAsState()
    val commentsMap by feed.comments.collectAsState()
    val blockedRiders by moderation.blockedRiders.collectAsState()

    var mode by remember { mutableStateOf(FeedMode.Feed) }
    var commentsFor by remember { mutableStateOf<FeedPost?>(null) }
    var draft by remember { mutableStateOf("") }

    val dateFmt = remember { SimpleDateFormat("M/d/yy • h:mm a", Locale.getDefault()) }
    val myInitials = remember(auth.prefsSnapshot.riderName) { auth.prefsSnapshot.riderName.rideInitials() }

    LaunchedEffect(Unit) { friends.start(); moderation.start() }
    LaunchedEffect(following, blockedRiders) {
        feed.listenForFeed(friends.followingIds(), blockedRiders.map { it.id }.toSet())
    }
    LaunchedEffect(commentsFor?.id) { commentsFor?.let { feed.listenForComments(it.id) } }

    DisposableEffect(Unit) {
        onDispose {
            feed.stopListening()
            friends.stop()
            moderation.stop()
        }
    }

    val displayedPosts = when (mode) {
        FeedMode.Feed -> posts.filter { !it.isLapSession }
        FeedMode.Mine -> posts.filter { feed.myKnownIDs.contains(it.authorID) && !it.isLapSession }
        FeedMode.MyLaps -> posts.filter { feed.myKnownIDs.contains(it.authorID) && it.isLapSession }
    }

    Column(Modifier.fillMaxSize().background(Pr.bg)) {
        FeedHeaderBar(
            initials = myInitials,
            onRefresh = { feed.listenForFeed(friends.followingIds()) }
        )
        FeedModeToggle(mode = mode, onSelect = { mode = it })

        error?.let {
            Text(
                it,
                color = Color(0xFFD33B2C),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        when {
            loading && posts.isEmpty() -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pr.coral)
                }
            }
            displayedPosts.isEmpty() -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FeedEmptyState(mode = mode)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (mode == FeedMode.Feed && discover.isNotEmpty()) {
                        item(key = "suggested-friends") {
                            SuggestedFriendsRow(
                                users = discover.take(10),
                                requestedIds = requestedIds,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onFollow = {
                                    friends.sendFollowRequest(
                                        it,
                                        auth.prefsSnapshot.riderName.ifBlank { "Rider" },
                                        myInitials
                                    )
                                }
                            )
                        }
                    }
                    items(displayedPosts, key = { it.id }) { post ->
                        val isMine = feed.myKnownIDs.contains(post.authorID)
                        if (mode == FeedMode.MyLaps) {
                            LapFeedPostCard(
                                post = post,
                                isMine = isMine,
                                dateFmt = dateFmt,
                                onReact = { emoji -> feed.setReaction(post.id, emoji) },
                                onDelete = { onDone -> feed.deletePost(post.id, onDone) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        } else {
                            FeedPostCard(
                                post = post,
                                isMine = isMine,
                                dateFmt = dateFmt,
                                onReact = { emoji -> feed.setReaction(post.id, emoji) },
                                onBookmark = { feed.toggleBookmark(post.id, post.bookmarkedByMe) },
                                onDelete = { onDone -> feed.deletePost(post.id, onDone) },
                                onReport = { reason, onDone ->
                                    moderation.report("feedPost", post.id, post.authorID, reason, onDone)
                                },
                                onBlock = { onDone -> moderation.block(post.authorID, post.authorName, onDone) },
                                onOpenComments = {
                                    draft = ""
                                    commentsFor = post
                                }
                            )
                        }
                    }
                }
            }
        }

        AdBannerFooter()
    }

    commentsFor?.let { post ->
        FeedCommentsDialog(
            post = post,
            comments = commentsMap[post.id].orEmpty(),
            draft = draft,
            onDraftChange = { draft = it },
            canDeleteComment = { comment -> feed.myKnownIDs.contains(comment.userID) || feed.myKnownIDs.contains(post.authorID) },
            onPost = {
                feed.addComment(post.id, auth.prefsSnapshot.riderName, draft)
                draft = ""
            },
            onDeleteComment = { comment, onDone -> feed.deleteComment(post.id, comment.id, onDone) },
            onDismiss = { commentsFor = null }
        )
    }
}

// MARK: - Header

@Composable
private fun FeedHeaderBar(initials: String, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Pr.cardBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PACKRIDE",
                color = Pr.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.4.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRefresh)
                    .background(Pr.fieldBg)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh feed",
                    tint = Pr.ink,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            PrAvatar(initials = initials, size = 32.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
    }
}

// MARK: - Mode toggle

@Composable
private fun FeedModeToggle(mode: FeedMode, onSelect: (FeedMode) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Pr.cardBg)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            listOf(
                FeedMode.Feed to "Ride Feed",
                FeedMode.Mine to "My Rides",
                FeedMode.MyLaps to "My Laps"
            ).forEach { (m, label) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(m) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        color = if (mode == m) Pr.ink else Pr.muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (mode == m) Pr.coral else Color.Transparent)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
    }
}

// MARK: - Empty state

@Composable
private fun FeedEmptyState(mode: FeedMode, modifier: Modifier = Modifier) {
    val title = when (mode) {
        FeedMode.Feed -> "No rides yet"
        FeedMode.Mine -> "You haven't posted any rides"
        FeedMode.MyLaps -> "No lap sessions posted yet"
    }
    val subtitle = when (mode) {
        FeedMode.Feed -> "Follow riders in Friends to see their rides here."
        FeedMode.Mine -> "Finish a ride and choose \"Post to Feed\" to share it, or share a past ride from Ride History."
        FeedMode.MyLaps -> "Finish a Track Mode session and choose \"Post to Feed\" to share your lap times here."
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(Pr.coralSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = Pr.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Pr.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// MARK: - Reaction button (tap = quick 👍 toggle, long-press = emoji picker)
// Port of iOS's contextMenu-based ReactionMenuItems. combinedClickable needs
// its own OptIn, scoped to just this composable.

private val reactionEmojiOptions = listOf("🔥", "❤️", "👍", "🤘")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReactionButton(
    myReaction: String?,
    reactionCount: Int,
    iconMode: Boolean,
    onReact: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onReact(if (myReaction != null) null else "👍") },
                    onLongClick = { showPicker = true }
                )
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconMode) {
                Icon(
                    imageVector = if (myReaction != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "React",
                    tint = if (myReaction != null) Pr.coral else Pr.muted,
                    modifier = Modifier.size(18.dp)
                )
                if (reactionCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$reactionCount",
                        color = if (myReaction != null) Pr.coral else Pr.muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(myReaction ?: "🤍", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (reactionCount > 0) "$reactionCount" else "React",
                    color = if (myReaction != null) Pr.coral else Pr.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        DropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
            reactionEmojiOptions.forEach { emoji ->
                DropdownMenuItem(
                    text = { Text(emoji, fontSize = 18.sp) },
                    onClick = { onReact(emoji); showPicker = false }
                )
            }
            if (myReaction != null) {
                DropdownMenuItem(
                    text = { Text("Remove Reaction", color = Color(0xFFD33B2C)) },
                    onClick = { onReact(null); showPicker = false }
                )
            }
        }
    }
}

// MARK: - Route preview map (non-interactive, port of iOS's FeedRouteMapView)

@Composable
private fun FeedRoutePreviewMap(route: List<FeedRoutePoint>, modifier: Modifier = Modifier) {
    val points = remember(route) { route.map { LatLng(it.lat, it.lng) } }
    if (points.size < 2) return
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 12f)
    }
    LaunchedEffect(points) {
        val bounds = LatLngBounds.Builder().apply { points.forEach { include(it) } }.build()
        runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 60)) }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(mapType = MapType.HYBRID),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        Polyline(points = points, color = Pr.coral, width = 4f)
        Marker(
            state = MarkerState(points.first()),
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        )
        Marker(
            state = MarkerState(points.last()),
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        )
    }
}

// MARK: - Feed post card (regular ride post) — port of iOS FeedPostCard.
// iOS deliberately keeps this one square-cornered with just a bottom
// hairline (not the rounded PrCard look LapFeedPostCard uses below) — kept
// that distinction rather than flattening both to the same shape.

@Composable
private fun FeedPostCard(
    post: FeedPost,
    isMine: Boolean,
    dateFmt: SimpleDateFormat,
    onReact: (String?) -> Unit,
    onBookmark: () -> Unit,
    onDelete: ((String?) -> Unit) -> Unit,
    onReport: (String, (String?) -> Unit) -> Unit,
    onBlock: ((String?) -> Unit) -> Unit,
    onOpenComments: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pr.cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (post.isAnonymous) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Pr.fieldBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "Anonymous",
                        tint = Pr.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                PrAvatar(initials = post.authorInitials, size = 40.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(post.authorName, color = Pr.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(dateFmt.format(Date((post.timestamp * 1000).toLong())), color = Pr.muted, fontSize = 11.sp)
            }
            Box {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showMenu = true }
                        .padding(6.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Post options",
                        tint = Pr.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isMine) {
                        DropdownMenuItem(
                            text = { Text("Delete post") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { showMenu = false; showDeleteConfirm = true }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Report post") },
                            leadingIcon = { Icon(Icons.Default.Flag, null) },
                            onClick = { showMenu = false; showReport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Block ${post.authorName}") },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                            onClick = { showMenu = false; showBlockConfirm = true }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(post.title, color = Pr.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row {
                Text(post.distanceString, color = Pr.muted, fontSize = 13.sp)
                Text(" · ", color = Pr.muted, fontSize = 13.sp)
                Text(post.duration, color = Pr.muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
        }

        // Photo takes priority over the route map, exactly like iOS's
        // if/else-if in RideFeedView.swift — a post can have a route, a photo,
        // both, or neither, but only one preview is shown when both exist.
        if (!post.photoURL.isNullOrBlank()) {
            AsyncImage(
                model = post.photoURL,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(240.dp)
            )
        } else if (post.route.size >= 2) {
            FeedRoutePreviewMap(
                route = post.route,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))

        Row(Modifier.fillMaxWidth()) {
            ReactionButton(
                modifier = Modifier.weight(1f),
                myReaction = post.myReaction,
                reactionCount = post.reactionCount,
                iconMode = true,
                onReact = onReact
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenComments)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment,
                        contentDescription = "Comments",
                        tint = Pr.muted,
                        modifier = Modifier.size(18.dp)
                    )
                    if (post.commentCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("${post.commentCount}", color = Pr.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { shareFeedPost(context, post) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Pr.muted, modifier = Modifier.size(18.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBookmark)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (post.bookmarkedByMe) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (post.bookmarkedByMe) Pr.coral else Pr.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
    }
    if (showReport) {
        ReportReasonDialog(
            onDismiss = { showReport = false },
            onReason = { reason ->
                showReport = false
                onReport(reason) { err -> actionMessage = err ?: "Report sent. Thank you for helping keep PackRide safe." }
            }
        )
    }
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block ${post.authorName}?") },
            text = { Text("You won't see this rider's posts. You can unblock them later in Privacy & Data.") },
            confirmButton = { TextButton(onClick = {
                showBlockConfirm = false
                onBlock { err -> actionMessage = err ?: "${post.authorName} blocked" }
            }) { Text("Block", color = Color(0xFFD33B2C)) } },
            dismissButton = { TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") } }
        )
    }
    actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { actionMessage = null }) { Text("OK") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this post?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete { err -> if (err != null) deleteError = err }
                }) { Text("Delete", color = Color(0xFFD33B2C)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
    deleteError?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("Couldn't Delete Post") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun ReportReasonDialog(onDismiss: () -> Unit, onReason: (String) -> Unit) {
    val reasons = listOf("Harassment or bullying", "Hate or abuse", "Dangerous content", "Spam or scam", "Privacy or safety", "Other")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report post") },
        text = {
            Column { reasons.forEach { reason ->
                TextButton(onClick = { onReason(reason) }, modifier = Modifier.fillMaxWidth()) {
                    Text(reason, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
            } }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// MARK: - Lap feed post card — port of iOS LapFeedPostCard.

@Composable
private fun LapFeedPostCard(
    post: FeedPost,
    isMine: Boolean,
    dateFmt: SimpleDateFormat,
    onReact: (String?) -> Unit,
    onDelete: ((String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    PrCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrAvatar(initials = post.authorInitials, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorName, color = Pr.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(dateFmt.format(Date((post.timestamp * 1000).toLong())), color = Pr.muted, fontSize = 11.sp)
                }
                if (isMine) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showDeleteConfirm = true }
                            .padding(6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Pr.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column {
                Text(post.title, color = Pr.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, contentDescription = null, tint = Pr.muted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        post.trackName.ifBlank { "Track Session" },
                        color = Pr.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LapStatBox(modifier = Modifier.weight(1f), value = "${post.lapTimes.size}", label = "Laps")
                LapStatBox(
                    modifier = Modifier.weight(1f),
                    value = if (post.bestLapTime > 0) LapEngine.formatLapTime(post.bestLapTime) else "--:--",
                    label = "Best Lap"
                )
                LapStatBox(
                    modifier = Modifier.weight(1f),
                    value = com.karthik.packride.data.MeasurementUnits.distanceMiles(post.distanceMiles),
                    label = "Distance"
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))

            Row(Modifier.fillMaxWidth()) {
                ReactionButton(
                    modifier = Modifier.weight(1f),
                    myReaction = post.myReaction,
                    reactionCount = post.reactionCount,
                    iconMode = false,
                    onReact = onReact
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { shareLapPost(context, post) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Pr.muted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", color = Pr.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this post?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete { err -> if (err != null) deleteError = err }
                }) { Text("Delete", color = Color(0xFFD33B2C)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
    deleteError?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("Couldn't Delete Post") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun LapStatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Pr.fieldBg)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Pr.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Pr.muted, fontSize = 10.sp)
    }
}

// MARK: - Suggested Friends row — port of iOS SuggestedFriendsRow.
// FriendsManager.follow() is a direct follow (no request/accept step on
// Android yet, unlike iOS's sendFollowRequest), so the button just fires
// follow() and the card disappears once the user drops off the discover
// list (FriendsManager filters out anyone already followed).

@Composable
private fun SuggestedFriendsRow(
    users: List<RiderProfile>,
    requestedIds: Set<String>,
    onFollow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PrCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Suggested Friends", color = Pr.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(users, key = { it.id }) { user ->
                    SuggestedFriendCard(
                        user = user,
                        requested = requestedIds.contains(user.id),
                        onFollow = { onFollow(user.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedFriendCard(user: RiderProfile, requested: Boolean, onFollow: () -> Unit) {
    Column(
        modifier = Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrAvatar(initials = user.initials, size = 52.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            user.name,
            color = Pr.ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (requested) Pr.fieldBg else Pr.coral)
                .clickable(enabled = !requested, onClick = onFollow)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                if (requested) "Sent" else "Follow",
                color = if (requested) Pr.muted else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// MARK: - Comments dialog — port of iOS FeedCommentsSheet, with
// deleteComment support the old stub didn't have.

@Composable
private fun FeedCommentsDialog(
    post: FeedPost,
    comments: List<FeedComment>,
    draft: String,
    onDraftChange: (String) -> Unit,
    canDeleteComment: (FeedComment) -> Boolean,
    onPost: () -> Unit,
    onDeleteComment: (FeedComment, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var commentPendingDelete by remember(post.id) { mutableStateOf<FeedComment?>(null) }
    var deleteCommentError by remember(post.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(post.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (comments.isEmpty()) {
                    Text("No comments yet", color = Pr.muted, fontSize = 13.sp)
                } else {
                    comments.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.userName, color = Pr.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(c.text, color = Pr.ink, fontSize = 14.sp)
                            }
                            if (canDeleteComment(c)) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { commentPendingDelete = c }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete comment",
                                        tint = Pr.muted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("Add a comment") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onPost, enabled = draft.isNotBlank()) { Text("Post") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    commentPendingDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentPendingDelete = null },
            title = { Text("Delete this comment?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteComment(comment) { err -> if (err != null) deleteCommentError = err }
                    commentPendingDelete = null
                }) { Text("Delete", color = Color(0xFFD33B2C)) }
            },
            dismissButton = {
                TextButton(onClick = { commentPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
    deleteCommentError?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteCommentError = null },
            title = { Text("Couldn't Delete Comment") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { deleteCommentError = null }) { Text("OK") } }
        )
    }
}

// MARK: - Share helpers — port of iOS's UIActivityViewController calls.

private fun shareFeedPost(context: Context, post: FeedPost) {
    RideShareCard.share(context, post.title, post.authorName, post.distanceString, post.duration)
}

private fun shareLapPost(context: Context, post: FeedPost) {
    val best = if (post.bestLapTime > 0) LapEngine.formatLapTime(post.bestLapTime) else "--:--"
    val text = "${post.authorName}'s track session: ${post.title} — ${post.lapTimes.size} laps, best $best on PackRide!"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Session"))
}
