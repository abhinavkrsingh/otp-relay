package com.otprelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class RelayForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "otp_relay_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: Android restarts the service if it's killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OTP Relay",
            NotificationManager.IMPORTANCE_LOW  // silent — no sound or vibration
        ).apply {
            description = "Keeps OTP relay running in the background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("OTP Relay")
            .setContentText("Monitoring for OTPs…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)       // cannot be dismissed by user
            .build()                // silence handled by IMPORTANCE_LOW on the channel
}
