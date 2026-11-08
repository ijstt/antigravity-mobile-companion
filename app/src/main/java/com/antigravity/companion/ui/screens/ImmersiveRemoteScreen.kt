package com.antigravity.companion.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antigravity.companion.notifications.WebNotificationBridge
import com.antigravity.companion.service.AntigravityKeepAliveService

@Composable
fun ImmersiveRemoteScreen(
    remoteUrl: String,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    // File upload picker state
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    // Keep session alive in background
    DisposableEffect(Unit) {
        AntigravityKeepAliveService.start(context)
        onDispose { }
    }

    // Intercept Android hardware back button
    BackHandler {
        if (canGoBack && webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Antigravity Session") },
            text = { Text("Would you like to link a different desktop, switch accounts, or reload the session?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onUnlink()
                }) {
                    Text("Switch Desktop (Scan QR)")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitDialog = false
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.removeAllCookies {
                            cookieManager.flush()
                            webViewRef?.loadUrl(remoteUrl)
                        }
                    }) {
                        Text("Switch Account")
                    }
                    TextButton(onClick = {
                        showExitDialog = false
                        webViewRef?.reload()
                    }) {
                        Text("Reload")
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF131314))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setBackgroundColor(android.graphics.Color.parseColor("#131314"))
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Clean mobile Chrome User Agent allows Google OAuth sign-in without WebView restrictions
                        userAgentString = "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this@apply, true)

                    // Add JavaScript bridge for native notifications
                    addJavascriptInterface(WebNotificationBridge(ctx), "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            canGoBack = view?.canGoBack() == true
                            CookieManager.getInstance().flush()

                            // Inject Notification polyfill and task completion observer
                            val jsBridge = """
                                (function() {
                                    if (!window.Notification) {
                                        window.Notification = function(title, options) {
                                            var body = (options && options.body) || '';
                                            if (window.AndroidBridge && window.AndroidBridge.postNotification) {
                                                window.AndroidBridge.postNotification(title, body);
                                            }
                                        };
                                        window.Notification.permission = 'granted';
                                        window.Notification.requestPermission = function(cb) {
                                            if (cb) cb('granted');
                                            return Promise.resolve('granted');
                                        };
                                    }

                                    if (!window.__agObserverActive) {
                                        window.__agObserverActive = true;
                                        var wasGenerating = false;
                                        setInterval(function() {
                                            try {
                                                var stopBtn = document.querySelector('button[aria-label*="Stop" i], button[title*="Stop" i], [data-test-id*="stop" i]');
                                                var isGenerating = !!stopBtn;
                                                if (wasGenerating && !isGenerating) {
                                                    if (window.AndroidBridge && window.AndroidBridge.onTaskCompleted) {
                                                        window.AndroidBridge.onTaskCompleted('Antigravity task completed.');
                                                    }
                                                }
                                                wasGenerating = isGenerating;
                                            } catch (e) {}
                                        }, 1000);
                                    }
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(jsBridge, null)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                            if (newProgress >= 100) {
                                isLoading = false
                            }
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }

                        override fun onShowFileChooser(
                            view: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            fileUploadCallback?.onReceiveValue(null)
                            fileUploadCallback = filePathCallback
                            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            return try {
                                fileChooserLauncher.launch(intent)
                                true
                            } catch (_: Exception) {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = null
                                false
                            }
                        }
                    }

                    loadUrl(remoteUrl)
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
            },
            modifier = Modifier.fillMaxSize()
        )

        // Ultra-thin 2dp loading progress indicator at the very top edge
        AnimatedVisibility(
            visible = isLoading && progress < 1.0f,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF0284C7),
                trackColor = Color.Transparent
            )
        }
    }
}
