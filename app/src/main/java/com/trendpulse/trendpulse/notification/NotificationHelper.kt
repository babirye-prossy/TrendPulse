package com.trendpulse.trendpulse.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.trendpulse.trendpulse.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "trendpulse_notifications"
        const val CHANNEL_NAME = "TrendPulse Updates"
        const val TOPIC_ALERTS = "alerts"
    }

    init {
        createNotificationChannel()
        // Automatically subscribe to a default topic for testing
        subscribeToTopic(TOPIC_ALERTS)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General TrendPulse Notifications"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun subscribeToTopic(topic: String) {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener { task ->
                    val msg = if (task.isSuccessful) "Subscribed to $topic" else "Subscription failed"
                    Log.d("NotificationHelper", msg)
                }
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Firebase not initialized: ${e.message}")
        }
    }

    fun showSimpleNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (e: SecurityException) {
                // Handle missing POST_NOTIFICATIONS permission on API 33+
            }
        }
    }
}
