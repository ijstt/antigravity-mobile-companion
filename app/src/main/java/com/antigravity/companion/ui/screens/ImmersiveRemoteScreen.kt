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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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

    val notificationBridgeScript = """
        (function() {
            // 1. Web Notification API Polyfill
            function AGNotification(title, options) {
                this.title = title;
                this.options = options || {};
                var body = (this.options && this.options.body) || '';
                if (window.AndroidBridge && window.AndroidBridge.postNotification) {
                    window.AndroidBridge.postNotification(title, body);
                } else {
                    try { window.top.postMessage({ type: 'AG_POST_NOTIFICATION', title: title, body: body }, '*'); } catch(e) {}
                }
            }
            AGNotification.permission = 'granted';
            AGNotification.requestPermission = function(cb) {
                if (typeof cb === 'function') cb('granted');
                return Promise.resolve('granted');
            };
            AGNotification.prototype.addEventListener = function() {};
            AGNotification.prototype.removeEventListener = function() {};
            AGNotification.prototype.close = function() {};

            window.Notification = AGNotification;
            try { Object.defineProperty(window, 'Notification', { value: AGNotification, writable: false }); } catch(e) {}

            // 2. Cross-frame message relay for AndroidBridge
            if (window.AndroidBridge) {
                window.addEventListener('message', function(e) {
                    if (!e.data || typeof e.data !== 'object') return;
                    if (e.data.type === 'AG_TASK_COMPLETED') {
                        window.AndroidBridge.onTaskCompleted(e.data.summary || 'Antigravity task completed.');
                    } else if (e.data.type === 'AG_POST_NOTIFICATION') {
                        window.AndroidBridge.postNotification(e.data.title || 'Antigravity', e.data.body || '');
                    }
                });
            }

            // 3. WebSocket traffic hook for instant background reactivity
            if (!window.__agWsHooked && window.WebSocket) {
                window.__agWsHooked = true;
                var OrigWebSocket = window.WebSocket;
                window.WebSocket = function(url, protocols) {
                    var ws = protocols ? new OrigWebSocket(url, protocols) : new OrigWebSocket(url);
                    try {
                        ws.addEventListener('message', function() {
                            if (window.__agCheckWorkingState) {
                                window.__agCheckWorkingState();
                            }
                        });
                    } catch(e) {}
                    return ws;
                };
                window.WebSocket.prototype = OrigWebSocket.prototype;
                try {
                    window.WebSocket.CONNECTING = OrigWebSocket.CONNECTING;
                    window.WebSocket.OPEN = OrigWebSocket.OPEN;
                    window.WebSocket.CLOSING = OrigWebSocket.CLOSING;
                    window.WebSocket.CLOSED = OrigWebSocket.CLOSED;
                } catch(e) {}
            }

            // 4. Multi-signal agent state observer (Only active in chat frames)
            if (!window.__agObserverInstalled) {
                window.__agObserverInstalled = true;
                var isWorking = false;
                var turnStartTime = 0;
                var completionTimer = null;
                var lastNotifiedTime = 0;

                function notifyTaskCompleted() {
                    var now = Date.now();
                    if (now - lastNotifiedTime < 3000) return;
                    lastNotifiedTime = now;
                    isWorking = false;
                    if (window.AndroidBridge && window.AndroidBridge.onTaskCompleted) {
                        window.AndroidBridge.onTaskCompleted('Antigravity task completed.');
                    } else {
                        try {
                            window.top.postMessage({ type: 'AG_TASK_COMPLETED', summary: 'Antigravity task completed.' }, '*');
                        } catch(e) {}
                    }
                }

                function isGeneratingNow() {
                    var buttons = document.querySelectorAll('button, [role="button"]');
                    for (var i = 0; i < buttons.length; i++) {
                        var btn = buttons[i];
                        var aria = (btn.getAttribute('aria-label') || '').toLowerCase();
                        var tooltip = (btn.getAttribute('data-tooltip-id') || '').toLowerCase();
                        var text = (btn.innerText || btn.textContent || '').trim().toLowerCase();

                        if (tooltip.includes('cancel') || tooltip.includes('stop') ||
                            aria.includes('cancel') || aria.includes('stop') || aria.includes('останов') || aria.includes('прерв') || aria.includes('стоп') ||
                            text === 'stop' || text === 'square' || text.includes('останов') || text.includes('прерв')) {
                            return true;
                        }
                    }

                    var matIcons = document.querySelectorAll('mat-icon');
                    for (var j = 0; j < matIcons.length; j++) {
                        var iconText = (matIcons[j].textContent || '').trim().toLowerCase();
                        if (iconText === 'stop' || iconText === 'pause' || iconText === 'stop_circle') {
                            return true;
                        }
                    }

                    if (document.querySelector('mat-progress-spinner, mat-spinner, .animate-spin, .generating-indicator, .streaming, [data-test-id*="stop" i]')) {
                        return true;
                    }

                    return false;
                }

                function checkWorkingState() {
                    // Ignore frames that don't contain any chat input or cancel button
                    var hasChatElements = !!document.querySelector(
                        'textarea, [contenteditable="true"], button[aria-label*="Cancel" i], [data-tooltip-id*="cancel" i], [data-tooltip-id*="input-send-button" i]'
                    );
                    if (!hasChatElements) return;

                    var generating = isGeneratingNow();

                    if (generating) {
                        if (!isWorking) {
                            isWorking = true;
                            turnStartTime = Date.now();
                        }
                        if (completionTimer) {
                            clearTimeout(completionTimer);
                            completionTimer = null;
                        }
                    } else if (isWorking) {
                        // Stabilize across tool execution pauses (wait 3.5s of continuous idle before notifying)
                        if (!completionTimer) {
                            var now = Date.now();
                            if (now - turnStartTime > 2500) {
                                completionTimer = setTimeout(function() {
                                    completionTimer = null;
                                    if (isWorking && !isGeneratingNow()) {
                                        notifyTaskCompleted();
                                    }
                                }, 3500);
                            }
                        }
                    }
                }
                window.__agCheckWorkingState = checkWorkingState;

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var btn = target.closest('button, [role="button"]');
                    if (!btn) return;
                    var aria = (btn.getAttribute('aria-label') || '').toLowerCase();
                    var tooltip = (btn.getAttribute('data-tooltip-id') || '').toLowerCase();
                    var text = (btn.innerText || btn.textContent || '').trim().toLowerCase();

                    // Ignore header feedback buttons
                    if (aria.includes('feedback') || tooltip.includes('feedback')) return;

                    if (tooltip.includes('input-send-button') || aria.includes('send') || aria.includes('отправ') ||
                        text === 'send' || text === 'arrow_forward' || text === 'arrow_upward' || text.includes('отправ')) {
                        isWorking = true;
                        turnStartTime = Date.now();
                        if (completionTimer) {
                            clearTimeout(completionTimer);
                            completionTimer = null;
                        }
                    }
                }, true);

                document.addEventListener('keydown', function(e) {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        var target = e.target;
                        if (target && (target.tagName === 'TEXTAREA' || target.getAttribute('contenteditable') === 'true')) {
                            isWorking = true;
                            turnStartTime = Date.now();
                            if (completionTimer) {
                                clearTimeout(completionTimer);
                                completionTimer = null;
                            }
                        }
                    }
                }, true);

                var observer = new MutationObserver(function() {
                    checkWorkingState();
                });

                function startObserving() {
                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true, attributes: true });
                        checkWorkingState();
                    } else {
                        setTimeout(startObserving, 50);
                    }
                }
                startObserving();
                setInterval(checkWorkingState, 1000);
            }
        })();
    """.trimIndent()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF131314))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView.setWebContentsDebuggingEnabled(true)
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
                        userAgentString = "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this@apply, true)

                    // Add JavaScript bridge for native notifications
                    addJavascriptInterface(WebNotificationBridge(ctx), "AndroidBridge")

                    // Inject Notification polyfill before ANY script or HTML executes
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(this@apply, notificationBridgeScript, setOf("*"))
                    }

                    resumeTimers()

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            canGoBack = view?.canGoBack() == true
                            view?.evaluateJavascript(notificationBridgeScript, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            canGoBack = view?.canGoBack() == true
                            CookieManager.getInstance().flush()
                            view?.evaluateJavascript(notificationBridgeScript, null)
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
                            android.util.Log.i("ImmersiveRemoteScreen", "WebView onPermissionRequest: " + (request?.resources?.joinToString() ?: "none"))
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
