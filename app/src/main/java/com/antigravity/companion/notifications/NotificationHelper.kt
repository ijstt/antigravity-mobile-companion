package com.antigravity.companion.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antigravity.companion.MainActivity
import com.antigravity.companion.R

object NotificationHelper {

    const val CHANNEL_TASKS_ID = "antigravity_tasks_channel"
    const val CHANNEL_SERVICE_ID = "antigravity_service_channel"
    const val SERVICE_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val tasksChannel = NotificationChannel(
                CHANNEL_TASKS_ID,
                "Antigravity Task Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when an Antigravity agent completes work or requires input"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Antigravity Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps remote session connected and responsive in background"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(tasksChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun showTaskNotification(context: Context, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS may not be granted yet
        }
    }

    fun createServiceNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Antigravity Session Active")
            .setContentText("Connected to desktop agent session")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
