package com.antigravity.companion.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antigravity.companion.MainActivity
import com.antigravity.companion.R

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_TASKS_ID = "antigravity_tasks_v2"
    const val CHANNEL_SERVICE_ID = "antigravity_service_v2"
    const val SERVICE_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val tasksChannel = NotificationChannel(
                CHANNEL_TASKS_ID,
                "Antigravity Task Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Audible alerts when Antigravity finishes a task or requires confirmation"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                setShowBadge(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Antigravity Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps remote session connected and responsive in background"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(tasksChannel)
            notificationManager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channels initialized successfully")
        }
    }

    fun showTaskNotification(context: Context, title: String, content: String) {
        Log.i(TAG, "Triggering task notification: '$title' - '$content'")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.i(TAG, "Notification posted with ID: $notificationId")

            // Wake screen briefly so user sees the task completion on lockscreen
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Antigravity::TaskAlertWake"
            )
            screenWakeLock?.acquire(3000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post task notification", e)
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
