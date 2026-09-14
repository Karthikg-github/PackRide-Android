package com.karthik.packride.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.karthik.packride.MainActivity
import com.karthik.packride.R
import com.karthik.packride.community.CommunityMembershipStore

/**
 * FCM handler — saves token under users/{uid}/fcmToken, and fans it out to
 * every community-membership record this device holds (iOS
 * NotificationManager.saveTokenToFirebase parity — two identity schemes,
 * Firebase Auth uid for follow/feed, device ID for communities).
 */
class PackRideMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_SAFETY = "packride_safety"
        const val CHANNEL_UPDATES = "packride_updates"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs = getSharedPreferences("packride_prefs", MODE_PRIVATE)
        prefs.edit().putString("fcmToken", token).apply()

        // Auth-uid-keyed copy — solo-ride "your followers get notified" pushes.
        // (Not an early return: the community fan-out below applies whether
        // or not the device is signed in, same as iOS.)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            // Keep the legacy scalar during migration, while preserving one token
            // per installation so signing in on iPhone no longer disables Android.
            userRef.child("fcmToken").setValue(token)
            if (!deviceId.isNullOrBlank()) userRef.child("fcmTokens").child("android-$deviceId").setValue(token)
        }

        // Device-ID-keyed copy on every community membership record this
        // device belongs to — "someone in your community started riding"
        // pushes. Was previously never written on Android at all.
        CommunityMembershipStore.get().writeFcmTokenToJoinedCommunities(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "PackRide"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "New notification"
        showNotification(title, body, message.data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val type = data["packrideType"].orEmpty()
        val isSafety = type == "crashIncident" || type == "helpRequest"
        val channelId = if (isSafety) CHANNEL_SAFETY else CHANNEL_UPDATES
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pi = PendingIntent.getActivity(
            this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (isSafety) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isSafety) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_SOCIAL)
            .build()
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }
}
