package com.karthik.packride.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.karthik.packride.MainActivity
import com.karthik.packride.R

/** Foreground lifetime for an active helmet/headset voice session. */
class VoiceCommsService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ride Comms", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Active PackRide rider voice connection"
                    setShowBadge(false)
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ride Comms active")
            .setContentText("Using your microphone for rider voice")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "packride_voice"
        private const val NOTIFICATION_ID = 4207
    }
}
