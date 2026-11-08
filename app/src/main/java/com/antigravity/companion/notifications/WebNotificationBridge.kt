package com.antigravity.companion.notifications

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

class WebNotificationBridge(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastNotificationTime: Long = 0

    @JavascriptInterface
    fun postNotification(title: String?, body: String?) {
        Log.i(TAG, "Bridge postNotification: title='$title', body='$body'")
        val safeTitle = if (title.isNullOrBlank()) "Google Antigravity" else title
        val safeBody = if (body.isNullOrBlank()) "Agent update received" else body

        mainHandler.post {
            NotificationHelper.showTaskNotification(context, safeTitle, safeBody)
        }
    }

    @JavascriptInterface
    fun onTaskCompleted(summary: String?) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationTime < 3000) {
            return
        }
        lastNotificationTime = now
        Log.i(TAG, "Bridge onTaskCompleted: summary='$summary'")

        val title = "Antigravity"
        val body = if (summary.isNullOrBlank()) "Agent finished running step." else summary

        mainHandler.post {
            NotificationHelper.showTaskNotification(context, title, body)
        }
    }

    @JavascriptInterface
    fun log(msg: String?) {
        Log.d("AG_WEB", msg ?: "null")
    }

    companion object {
        private const val TAG = "WebNotificationBridge"
    }
}
