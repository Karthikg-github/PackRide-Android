package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.friends.RiderProfile
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.Pr
import kotlinx.coroutines.delay

@Composable
fun FriendsScreen(auth: AuthManager? = null) {
    val context = LocalContext.current
    val manager = remember { FriendsManager(context) }
    val following by manager.followedUsers.collectAsState()
    val followers by manager.followers.collectAsState()
    val discover by manager.discover.collectAsState()
    val requestedIds by manager.requestedIds.collectAsState()
    val myLoc by SharedLocationManager.get().location.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        manager.start()
        SharedLocationManager.get().startUpdating("friendsMap")
        manager.setOnline(true)
    }
    LaunchedEffect(myLoc) {
        myLoc?.let { manager.publishMyLocation(it) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            SharedLocationManager.get().location.value?.let { manager.publishMyLocation(it) }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            manager.setOnline(false)
            manager.stop()
            SharedLocationManager.get().stopUpdating("friendsMap")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Friends",
            style = MaterialTheme.typography.headlineMedium,
            color = PrCoral,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)
        )
        TabRow(selectedTabIndex = tab) {
            listOf("Following", "Followers", "Discover", "Map").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> RiderList(following, "Not following anyone yet.", { "Unfollow" }) { manager.unfollow(it.id) }
            1 -> RiderList(followers, "No followers yet.", { "Remove" }) { manager.removeFollower(it.id) }
            2 -> RiderList(
                discover,
                "No riders to discover.",
                { if (requestedIds.contains(it.id)) "Sent" else "Follow" },
                actionEnabled = { !requestedIds.contains(it.id) }
            ) {
                val name = auth?.prefsSnapshot?.riderName?.ifBlank { "Rider" } ?: "Rider"
                manager.sendFollowRequest(it.id, name, name.rideInitials())
            }
            3 -> FriendsMap(following = following, myLoc = myLoc)
        }
    }
}

@Composable
private fun RiderList(
    riders: List<RiderProfile>,
    empty: String,
    actionLabel: (RiderProfile) -> String?,
    actionEnabled: (RiderProfile) -> Boolean = { true },
    onAction: (RiderProfile) -> Unit
) {
    if (riders.isEmpty()) {
        Text(empty, modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(riders, key = { it.id }) { r ->
            Column(Modifier.fillMaxWidth().background(Pr.cardBg)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.initials} · ${r.name}", style = MaterialTheme.typography.titleSmall)
                        val sub = listOf(
                            r.city, r.bike,
                            if (r.isOnline) "Online" else "",
                            r.experience
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                    }
                    actionLabel(r)?.let { label ->
                        OutlinedButton(
                            onClick = { onAction(r) },
                            enabled = actionEnabled(r)
                        ) { Text(label) }
                    }
                }
                HorizontalDivider(color = Pr.border)
            }
        }
    }
}

@Composable
private fun FriendsMap(
    following: List<RiderProfile>,
    myLoc: android.location.Location?
) {
    val withCoords = following.filter { it.lat != null && it.lng != null }
    val start = myLoc?.let { LatLng(it.latitude, it.longitude) }
        ?: withCoords.firstOrNull()?.let { LatLng(it.lat!!, it.lng!!) }
        ?: LatLng(37.77, -122.42)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, 11f)
    }
    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = com.google.maps.android.compose.MapProperties(mapType = com.google.maps.android.compose.MapType.HYBRID)
        ) {
            myLoc?.let {
                Marker(state = MarkerState(LatLng(it.latitude, it.longitude)), title = "You")
            }
            withCoords.forEach { r ->
                Marker(
                    state = MarkerState(LatLng(r.lat!!, r.lng!!)),
                    title = r.name,
                    snippet = listOf(r.bike, r.city).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }
        }
        Text(
            if (withCoords.isEmpty()) "Follow riders who share location to see them here."
            else "${withCoords.size} friends on map",
            modifier = Modifier.padding(12.dp),
            color = PrCoral,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
