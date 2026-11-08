package com.antigravity.companion.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("NotificationTestReceiver", "Received test notification broadcast")
        val title = intent?.getStringExtra("title") ?: "Antigravity Task Finished"
        val body = intent?.getStringExtra("body") ?: "Your agent completed execution."
        NotificationHelper.showTaskNotification(context, title, body)
    }
}
