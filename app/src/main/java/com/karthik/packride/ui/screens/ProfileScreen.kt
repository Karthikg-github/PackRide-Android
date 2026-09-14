package com.karthik.packride.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.karthik.packride.ads.AdBannerFooter
import com.karthik.packride.ads.AdManager
import com.karthik.packride.alerts.CrashAlertManager
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.data.MeasurementSystem
import com.karthik.packride.data.MeasurementUnits
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.friends.LocationVisibilitySettings
import com.karthik.packride.friends.RiderProfile
import com.karthik.packride.safety.EmergencyContactStore
import com.karthik.packride.storage.ImageCloudUpload
import com.karthik.packride.profile.ProfileImageResolver
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrPill
import com.karthik.packride.ui.theme.PrWebSectionLabel
import com.karthik.packride.ui.theme.ThemePreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.compose.AsyncImage

// Aug 30, 2026 — Profile visual+functionality parity pass. Was a bare
// always-editable 4-field form with no design-system styling, no dark-mode
// toggle, no blood-type/allergies, and a Delete Account button with zero
// confirmation. Rebuilt against ProfileView.swift with real edit-toggle
// sections, a wired Dark Mode switch (see ThemePreference.kt), Emergency &
// Safety fields (see UserPrefs.bloodType/allergies), a Device ID row
// matching iOS's copy-to-clipboard behavior, and a confirmation dialog
// before account deletion. Crash Alerts (already real) and Sign Out are
// carried over unchanged.
//
// Aug 31, 2026 — avatar photo upload closed the "no image upload anywhere"
// gap flagged below: tapping the avatar now opens the system photo picker,
// uploads to Firebase Storage at users/{uid}/avatar.jpg (same path iOS's
// FirebaseManager.uploadProfileImage uses, so a photo set on either platform
// shows up on both), and publishes the URL to users/{uid}/profile/avatarURL.
// Banner photo (the other half of that iOS PhotosPicker pass) stays out of
// scope — only the avatar gap was flagged for this pass.
//
// Deliberately skipped this pass, flagged rather than faked:
// - Follow-request notification bell / NotificationCenterView — confirmed by
//   reading FriendsManager.kt that Android's follow model is direct
//   follow()/unfollow() only; there is no request/approval concept to build
//   a notification center around without a separate subsystem first.
// - Nearby-riders proximity alerts (UserProfileManager.listenForNearbyRiders)
//   — a separate location subsystem, not really "my own profile" content.
// - Full embedded emergency-contacts editor — Android already has a real,
//   working one in CrashDetectionScreen's EmergencyContactsSection (reachable
//   via SafetyHubScreen's Crash sub-tab); this screen shows a summary and
//   links there via PendingSafetyTab.request(0) rather than duplicating it.
//   (Aug 31, 2026 — previously linked to the standalone EmergencyContactsScreen
//   at MoreScreen's "SOS" tab; that tab was removed as a duplicate of this
//   same CrashDetectionScreen section, so onOpenSafety now targets the Crash
//   sub-tab directly instead.)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    auth: AuthManager,
    onOpenGarage: () -> Unit = {},
    onOpenSafety: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {}
) {
    val context = LocalContext.current
    val p = auth.prefsSnapshot
    val clipboard = LocalClipboardManager.current

    var isEditingBike by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(p.riderName) }
    var bike by remember { mutableStateOf(p.riderBike) }
    var city by remember { mutableStateOf(p.riderCity) }
    var experience by remember { mutableStateOf(p.riderExperience) }

    var isEditingSafety by remember { mutableStateOf(false) }
    var bloodType by remember { mutableStateOf(p.bloodType) }
    var allergies by remember { mutableStateOf(p.allergies) }

    var status by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var copiedDeviceId by remember { mutableStateOf(false) }
    var showLocationAudience by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var isSendingFeedback by remember { mutableStateOf(false) }
    var feedbackError by remember { mutableStateOf<String?>(null) }
    var feedbackSentConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackSentConfirmation) {
        if (feedbackSentConfirmation) {
            delay(3000)
            feedbackSentConfirmation = false
        }
    }

    val visibilitySettings = remember { LocationVisibilitySettings(context) }
    val visibility by visibilitySettings.state.collectAsState()
    val friendsManager = remember { FriendsManager(context) }
    val followers by friendsManager.followers.collectAsState()
    val following by friendsManager.followedUsers.collectAsState()
    val followRequests by friendsManager.followRequests.collectAsState()
    val historyManager = remember { RideHistoryManager(context) }
    val rides by historyManager.rides.collectAsState()
    val totalMiles = rides.sumOf { it.distanceMiles }

    // Aug 31, 2026 — avatar photo upload state. avatarUrl starts from whatever
    // was last synced from Firebase (UserPrefs.avatarURL, hydrated by
    // AuthManager on login) and is updated in place once a new photo finishes
    // uploading, so the hero avatar swaps to it without waiting for a
    // recomposition triggered elsewhere.
    var avatarUrl by remember { mutableStateOf(p.avatarURL) }
    var bannerUrl by remember { mutableStateOf(p.bannerURL) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var bannerBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingBanner by remember { mutableStateOf(false) }

    // Refresh the shared Firebase profile whenever this screen opens. Auth
    // hydration used to skip all server reads once onboarding was marked
    // complete, leaving an iOS-set avatar/banner invisible on Android.
    LaunchedEffect(auth.uid) {
        val uid = auth.uid ?: return@LaunchedEffect
        val root = FirebaseDatabase.getInstance().reference
        // Read the two mirrors independently. A denied/stale private-profile
        // read must not prevent Android from using the public copy written by
        // iOS, which was the old all-or-nothing failure mode here.
        val publicData = runCatching {
            root.child("publicRiders").child(uid).get().await().value as? Map<*, *>
        }.getOrNull().orEmpty()
        val privateData = runCatching {
            root.child("users").child(uid).child("profile").get().await().value as? Map<*, *>
        }.getOrNull().orEmpty()

        fun sharedValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            (privateData[key] as? String)?.takeIf(String::isNotBlank)
                ?: (publicData[key] as? String)?.takeIf(String::isNotBlank)
        }
        sharedValue("avatarURL", "avatarUrl", "profileImageURL", "photoURL")?.let {
            avatarUrl = it; p.avatarURL = it
        }
        sharedValue("bannerURL", "bannerUrl", "heroImageURL", "coverPhotoURL")?.let {
            bannerUrl = it; p.bannerURL = it
        }
        // Older iOS builds could upload the files successfully before their
        // RTDB URL mirror completed. Resolve the shared Storage paths too and
        // repair both mirrors so every screen/platform sees the same images.
        val resolvedImages = ProfileImageResolver.resolve(uid)
        if (resolvedImages.avatarUrl.isNotBlank()) { avatarUrl = resolvedImages.avatarUrl; p.avatarURL = resolvedImages.avatarUrl }
        if (resolvedImages.bannerUrl.isNotBlank()) { bannerUrl = resolvedImages.bannerUrl; p.bannerURL = resolvedImages.bannerUrl }
        val imageBytes = ProfileImageResolver.fetchBytes(uid)
        imageBytes.avatar?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { avatarBitmap = it.asImageBitmap() } }
        imageBytes.banner?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bannerBitmap = it.asImageBitmap() } }
        sharedValue("name")?.let { name = it; p.riderName = it }
        sharedValue("bike")?.let { bike = it; p.riderBike = it }
        sharedValue("city")?.let { city = it; p.riderCity = it }
        sharedValue("experience")?.let { experience = it; p.riderExperience = it }
        if (publicData.isEmpty() && privateData.isEmpty()) status = "Couldn't refresh Firebase profile"
    }
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            status = "Sign in to upload a photo."
            return@rememberLauncherForActivityResult
        }
        isUploadingAvatar = true
        ImageCloudUpload.uploadAvatar(context, uid, uri) { result ->
            isUploadingAvatar = false
            result.onSuccess { url ->
                avatarUrl = url
                p.avatarURL = url
                FirebaseDatabase.getInstance().reference.updateChildren(
                    mapOf(
                        "users/$uid/profile/avatarURL" to url,
                        "publicRiders/$uid/avatarURL" to url
                    )
                )
                status = "Profile photo updated"
            }.onFailure { e ->
                status = e.localizedMessage ?: "Photo upload failed"
            }
        }
    }
    val bannerPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@rememberLauncherForActivityResult
        isUploadingBanner = true
        ImageCloudUpload.uploadBanner(context, uid, uri) { result ->
            isUploadingBanner = false
            result.onSuccess { url ->
                bannerUrl = url
                p.bannerURL = url
                FirebaseDatabase.getInstance().reference.updateChildren(
                    mapOf(
                        "users/$uid/profile/bannerURL" to url,
                        "publicRiders/$uid/bannerURL" to url
                    )
                )
                status = "Cover photo updated"
            }.onFailure { status = it.localizedMessage ?: "Cover upload failed" }
        }
    }

    val privacyOptionsRequired by AdManager.privacyOptionsRequired.collectAsState()
    val themePreference = remember { ThemePreference.get() }
    val darkModeOverride by themePreference.override.collectAsState()
    val darkModeOn = darkModeOverride ?: false
    val measurementSystem by MeasurementUnits.system.collectAsState()

    val emergencyStore = remember { EmergencyContactStore(context) }
    // Not wrapped in remember{} — re-read on every recomposition so the count
    // reflects contacts added/removed on the SOS tab when the user returns
    // here (a stale-once-cached count would otherwise show 0 forever until
    // the app restarts). SharedPreferences read is cheap.
    val contactCount = emergencyStore.load().size

    val alertsMgr = remember { CrashAlertManager(context) }
    val alerts by alertsMgr.receivedAlerts.collectAsState()
    val fmt = remember { SimpleDateFormat("M/d/yy · h:mm a", Locale.getDefault()) }

    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    LaunchedEffect(Unit) {
        alertsMgr.listen()
        visibilitySettings.load()
        friendsManager.startFollowersOnly()
    }
    DisposableEffect(Unit) {
        onDispose {
            alertsMgr.stop()
            friendsManager.stop()
        }
    }

    LaunchedEffect(copiedDeviceId) {
        if (copiedDeviceId) {
            delay(2000)
            copiedDeviceId = false
        }
    }

    fun publish() {
        p.riderName = name.trim()
        p.riderBike = bike.trim()
        p.riderCity = city.trim()
        p.riderExperience = experience.trim()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val data = mapOf(
                "name" to name.trim(),
                "initials" to name.trim().rideInitials(),
                "bike" to bike.trim(),
                "city" to city.trim(),
                "experience" to experience.trim()
            )
            val updates = mutableMapOf<String, Any?>()
            data.forEach { (key, value) ->
                updates["users/$uid/profile/$key"] = value
                updates["publicRiders/$uid/$key"] = value
            }
            FirebaseDatabase.getInstance().reference.updateChildren(updates)
                .addOnSuccessListener { status = "Profile published" }
                .addOnFailureListener { e -> status = e.localizedMessage ?: "Failed" }
        } else status = "Saved locally"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // iOS Profile hero: full-width editable cover, dark readability fade,
        // overlaid avatar/name and notification/safety shortcuts.
        Box(Modifier.fillMaxWidth().height(327.dp).background(Pr.cover)) {
            if (bannerBitmap != null) {
                Image(
                    bitmap = bannerBitmap!!,
                    contentDescription = "Profile cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = "Profile cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = .28f), Color.Black.copy(alpha = .82f)))
                )
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PACKRIDE", color = Color.White, style = PrFont.caption)
                Spacer(Modifier.weight(1f))
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f))
                    ) { Icon(Icons.Filled.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(19.dp)) }
                    if (followRequests.isNotEmpty()) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(Pr.coral), contentAlignment = Alignment.Center) {
                            Text("${followRequests.size.coerceAtMost(99)}", color = Color.White, style = PrFont.micro)
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = onOpenSafety,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f))
                ) { Icon(Icons.Filled.Warning, "Safety", tint = Color.White, modifier = Modifier.size(19.dp)) }
            }
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap!!,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(68.dp).clip(CircleShape).clickable {
                            avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                } else PrAvatar(
                    initials = name.rideInitials(), size = 68.dp, photoUrl = avatarUrl,
                    modifier = Modifier.clickable {
                        avatarPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                if (isUploadingAvatar) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Change profile photo",
                            tint = Pr.coral,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        experience.ifBlank { "Intermediate" }.uppercase(),
                        color = Color.White,
                        style = PrFont.micro,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Pr.coral)
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                    Text(
                        "${rides.size} Rides  ·  ${MeasurementUnits.distanceMiles(totalMiles, 0)}  ·",
                        color = Color.White.copy(alpha = .86f), style = PrFont.caption
                    )
                    Text(
                        "${followers.size} Followers  ·  ${following.size} Following  ›",
                        color = Color.White.copy(alpha = .9f), style = PrFont.caption
                    )
                }
            }
            TextButton(
                onClick = { bannerPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 12.dp)
            ) {
                if (isUploadingBanner) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(5.dp))
                Text("Edit Cover", color = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))

        PrWebSectionLabel(title = "Live Location Privacy")
        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Share while riding with followers", style = PrFont.body, modifier = Modifier.weight(1f))
                    Switch(visibility.shareWithFollowers, visibilitySettings::setShareWithFollowers)
                }
                if (visibility.shareWithFollowers) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLocationAudience = true }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Choose followers", style = PrFont.body)
                            Text(
                                if (visibility.usesSelectedFollowers) "${visibility.selectedFollowerIds.size} selected" else "All followers",
                                style = PrFont.caption
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Share while riding with communities", style = PrFont.body, modifier = Modifier.weight(1f))
                    Switch(visibility.shareWithCommunities, visibilitySettings::setShareWithCommunities)
                }
                Text(
                    "Group Ride members can always see one another during that active ride. Need Help is shared only with the people you choose.",
                    style = PrFont.caption
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        PrWebSectionLabel(title = "Nearby Rider Radius", detail = MeasurementUnits.distanceMiles(visibility.nearbyRadiusMiles, 0))
        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Slider(
                    value = visibility.nearbyRadiusMiles.toFloat(),
                    onValueChange = { visibilitySettings.setNearbyRadiusMiles(it.toDouble()) },
                    valueRange = 1f..50f,
                    steps = 48
                )
                Text("Show and alert for riders within this distance while you are on a solo ride.", style = PrFont.caption)
            }
        }

        // My Bike section
        PrWebSectionLabel(
            title = "My Bike",
            detail = if (isEditingBike) "Save" else "Edit",
            modifier = Modifier.clickable {
                if (isEditingBike) publish()
                isEditingBike = !isEditingBike
            }
        )
        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isEditingBike) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(bike, { bike = it }, label = { Text("Bike") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(city, { city = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth())
                    Text("Experience", style = PrFont.caption)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Beginner", "Intermediate", "Advanced", "Expert").forEach { level ->
                            PrPill(title = level, selected = experience == level, onClick = { experience = level })
                        }
                    }
                } else {
                    ProfileInfoRow("Bike", bike.ifBlank { "Not set" })
                    ProfileInfoRow("City", city.ifBlank { "Not set" })
                    ProfileInfoRow("Experience", experience.ifBlank { "Not set" })
                }
                if (status.isNotEmpty()) Text(status, style = PrFont.caption)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Garage entry row
        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenGarage)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Garage", style = PrFont.subheading)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Emergency & Safety section
        PrWebSectionLabel(
            title = "Emergency & Safety",
            detail = if (isEditingSafety) "Save" else "Edit",
            modifier = Modifier.clickable {
                if (isEditingSafety) {
                    p.bloodType = bloodType.trim()
                    p.allergies = allergies.trim()
                }
                isEditingSafety = !isEditingSafety
            }
        )
        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isEditingSafety) {
                    Text("Blood type", style = PrFont.caption)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-").forEach { type ->
                            PrPill(title = type, selected = bloodType == type, onClick = { bloodType = type })
                        }
                    }
                    OutlinedTextField(allergies, { allergies = it }, label = { Text("Allergies") }, modifier = Modifier.fillMaxWidth())
                } else {
                    ProfileInfoRow("Blood type", bloodType.ifBlank { "Not set" })
                    ProfileInfoRow("Allergies", allergies.ifBlank { "None listed" })
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSafety)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (contactCount == 0) "No emergency contacts" else "$contactCount emergency contact${if (contactCount == 1) "" else "s"}",
                        style = PrFont.bodySmall
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // System settings
        PrWebSectionLabel(title = "System")
        Column(modifier = Modifier.fillMaxWidth().background(Pr.cardBg)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (darkModeOn) Pr.inkFixed else Pr.teal.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (darkModeOn) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            tint = if (darkModeOn) Color.White else Pr.teal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Dark Mode", style = PrFont.subheading)
                        Text(
                            if (darkModeOn) "PackRide is using its night palette" else "Use the lighter PackRide palette",
                            style = PrFont.caption
                        )
                    }
                    Switch(checked = darkModeOn, onCheckedChange = { themePreference.set(it) })
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Units", style = PrFont.subheading)
                    Text("Used for distance, speed, temperature and elevation", style = PrFont.caption)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            MeasurementSystem.IMPERIAL to "Imperial · mi, mph, °F",
                            MeasurementSystem.METRIC to "Metric · km, km/h, °C"
                        ).forEach { (system, label) ->
                            OutlinedButton(
                                onClick = { MeasurementUnits.set(context, system) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (measurementSystem == system) "✓ $label" else label,
                                    style = PrFont.caption
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboard.setText(AnnotatedString(deviceId))
                            copiedDeviceId = true
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Device ID", style = PrFont.body)
                        Text(deviceId, style = PrFont.caption, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (copiedDeviceId) Text("Copied", style = PrFont.caption)
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy device ID", tint = Pr.muted)
                    }
                }
                Text("UID: ${auth.uid ?: "—"}", style = PrFont.caption)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPrivacy),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Privacy & Data", style = PrFont.body)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        feedbackSentConfirmation = false
                        feedbackError = null
                        showFeedbackDialog = true
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Send Feedback", style = PrFont.body)
                        Text("Report a bug or request a feature", style = PrFont.caption)
                    }
                    if (feedbackSentConfirmation) {
                        Text("Sent!", style = PrFont.caption)
                    } else {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted)
                    }
                }
                if (privacyOptionsRequired) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                (context as? Activity)?.let { activity ->
                                    AdManager.showPrivacyOptions(activity) { error ->
                                        if (error != null) status = error
                                    }
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ad privacy choices", style = PrFont.body)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Crash alerts (unchanged, already real)
        PrWebSectionLabel(title = "Crash Alerts")
        if (alerts.isEmpty()) {
            Text(
                "No in-app crash alerts.",
                style = PrFont.caption,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                alerts.forEach { a ->
                    PrCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${a.senderName} may need help", style = PrFont.body)
                            a.peakG?.let { Text("Impact detected: ${"%.1f".format(it)} G", style = PrFont.caption) }
                            Text(fmt.format(Date((a.timestamp * 1000).toLong())), style = PrFont.caption)
                            OutlinedButton(onClick = {
                                alertsMgr.acknowledge(a, name.ifBlank { "A PackRide contact" }) { error ->
                                    if (error != null) status = error
                                }
                            }) { Text("I'm checking on them") }
                            a.mapURL?.let { url ->
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }) { Text("View map") }
                            }
                            OutlinedButton(onClick = { alertsMgr.dismiss(a.id) }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AdBannerFooter()
        Spacer(Modifier.height(8.dp))

        deleteError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = PrFont.caption,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = { auth.signOut() }, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "PackRide v$appVersion",
            style = PrFont.micro,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    if (showLocationAudience) {
        ModalBottomSheet(onDismissRequest = { showLocationAudience = false }) {
            LocationAudienceSheet(
                followers = followers,
                usesSelected = visibility.usesSelectedFollowers,
                selectedIds = visibility.selectedFollowerIds,
                onAll = visibilitySettings::shareWithAllFollowers,
                onSelected = visibilitySettings::useSelectedFollowers,
                onToggle = visibilitySettings::toggleFollower,
                onDone = { showLocationAudience = false }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = { Text("This permanently deletes your PackRide account and all synced data. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    auth.deleteAccount { err -> deleteError = err }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // feedback/{feedbackID} is append-only, no client read access (see
    // firebase-database.rules.json) — a Cloud Function (sendFeedbackEmail in
    // packride-functions/functions/index.js) watches that path and emails
    // the text straight to Karthik.
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSendingFeedback) showFeedbackDialog = false },
            title = { Text("Send Feedback") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A bug you hit, or a feature you'd like — this goes straight to the developer.", style = PrFont.caption)
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Tell us what's going on…") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                    feedbackError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = PrFont.caption) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = feedbackText.isNotBlank() && !isSendingFeedback,
                    onClick = {
                        val trimmed = feedbackText.trim()
                        isSendingFeedback = true
                        feedbackError = null
                        val entry = mapOf(
                            "senderUID" to (auth.uid ?: "unknown"),
                            "senderName" to name.ifBlank { "A PackRide rider" },
                            "senderEmail" to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
                            "platform" to "android",
                            "message" to trimmed,
                            "createdAt" to ServerValue.TIMESTAMP
                        )
                        FirebaseDatabase.getInstance().reference.child("feedback").push().setValue(entry)
                            .addOnSuccessListener {
                                isSendingFeedback = false
                                feedbackText = ""
                                showFeedbackDialog = false
                                feedbackSentConfirmation = true
                            }
                            .addOnFailureListener { e ->
                                isSendingFeedback = false
                                feedbackError = "Couldn't send that — ${e.localizedMessage ?: "try again"}"
                            }
                    }
                ) {
                    if (isSendingFeedback) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Send")
                }
            },
            dismissButton = {
                TextButton(enabled = !isSendingFeedback, onClick = { showFeedbackDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LocationAudienceSheet(
    followers: List<RiderProfile>,
    usesSelected: Boolean,
    selectedIds: Set<String>,
    onAll: () -> Unit,
    onSelected: () -> Unit,
    onToggle: (String) -> Unit,
    onDone: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Live-location audience", style = PrFont.heading, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done", color = Pr.coral) }
        }
        AudienceChoice(
            "All followers",
            "Anyone who follows you can see your location while you ride.",
            !usesSelected,
            onAll
        )
        AudienceChoice(
            "Only selected followers",
            "Pick exactly who can see your location.",
            usesSelected,
            onSelected
        )
        Text("Group Ride members can still see one another during an active room.", style = PrFont.caption)
        if (usesSelected) {
            Text("Selected followers", style = PrFont.subheading)
            if (followers.isEmpty()) {
                Text("You do not have followers yet.", style = PrFont.caption)
            } else {
                followers.forEach { follower ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(follower.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PrAvatar(initials = follower.initials, size = 36.dp, photoUrl = follower.avatarURL)
                        Column(Modifier.weight(1f)) {
                            Text(follower.name, style = PrFont.body)
                            Text(follower.city.ifBlank { "Follower" }, style = PrFont.caption)
                        }
                        val selected = selectedIds.contains(follower.id)
                        Icon(
                            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) Pr.coral else Pr.muted
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AudienceChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PrFont.body)
            Text(subtitle, style = PrFont.caption)
        }
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Pr.coral else Pr.muted
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = PrFont.caption)
        Text(value, style = PrFont.bodySmall)
    }
}
