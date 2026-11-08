package com.antigravity.companion.notifications

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class WebNotificationBridge(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastNotificationTime: Long = 0

    @JavascriptInterface
    fun postNotification(title: String?, body: String?) {
        val safeTitle = if (title.isNullOrBlank()) "Google Antigravity" else title
        val safeBody = if (body.isNullOrBlank()) "Agent update received" else body

        mainHandler.post {
            NotificationHelper.showTaskNotification(context, safeTitle, safeBody)
        }
    }

    @JavascriptInterface
    fun onTaskCompleted(summary: String?) {
        val now = System.currentTimeMillis()
        // Debounce notifications within 3 seconds
        if (now - lastNotificationTime < 3000) {
            return
        }
        lastNotificationTime = now

        val title = "Task Completed"
        val body = if (summary.isNullOrBlank()) "Antigravity finished running step." else summary

        mainHandler.post {
            NotificationHelper.showTaskNotification(context, title, body)
        }
    }
}
