package com.karthik.packride.location

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.karthik.packride.MainActivity
import com.karthik.packride.R
import com.karthik.packride.crash.CrashDetectionManager
import com.karthik.packride.help.HelpRequestManager

/**
 * Foreground service that keeps the process eligible for continuous location
 * while any background reason is active (rideTracking, lapTracking, needHelp,
 * crashDetection) — Android equivalent of iOS allowsBackgroundLocationUpdates.
 */
class LocationTrackingService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CRASH) {
            CrashDetectionManager.get().stopMonitoring()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        // START_STICKY can recreate this service after memory pressure. Restore
        // the rider's explicit protection choice so the sensor listener is not
        // silently lost with the old app process.
        if (CrashDetectionManager.shouldRestore(this)) {
            CrashDetectionManager.get().startMonitoring()
        }
        if (HelpRequestManager.shouldRestore(this)) {
            HelpRequestManager.get().restoreIfNeeded()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val crashEnabled = CrashDetectionManager.shouldRestore(this)
        val helpEnabled = HelpRequestManager.shouldRestore(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_title))
            .setContentText(
                when {
                    crashEnabled && helpEnabled -> "Crash protection and Need Help sharing are active"
                    crashEnabled -> "Crash protection and location are active"
                    helpEnabled -> "Need Help location sharing is active"
                    else -> getString(R.string.location_service_text)
                }
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (crashEnabled) {
            val stopCrash = PendingIntent.getService(
                this,
                2,
                Intent(this, LocationTrackingService::class.java).setAction(ACTION_STOP_CRASH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Stop crash protection", stopCrash)
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "packride_location"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_CRASH = "com.karthik.packride.STOP_CRASH_PROTECTION"
    }
}
