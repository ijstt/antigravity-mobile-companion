package com.antigravity.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.antigravity.companion.notifications.NotificationHelper
import com.antigravity.companion.service.AntigravityKeepAliveService
import com.antigravity.companion.ui.screens.ImmersiveRemoteScreen
import com.antigravity.companion.ui.screens.PairingScreen
import com.antigravity.companion.ui.theme.AntigravityTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handled silently
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize system notification channels
        NotificationHelper.createChannels(this)

        // Request runtime permissions if required (POST_NOTIFICATIONS on Android 13+)
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Set system status and navigation bars to dark theme matching Google Antigravity
        window.statusBarColor = android.graphics.Color.parseColor("#131314")
        window.navigationBarColor = android.graphics.Color.parseColor("#131314")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val prefs = getSharedPreferences("antigravity_mobile_config", Context.MODE_PRIVATE)

        setContent {
            AntigravityTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF131314)
                ) {
                    var remoteUrl by remember {
                        mutableStateOf(prefs.getString("google_remote_url", null)?.ifBlank { null })
                    }

                    if (remoteUrl != null) {
                        ImmersiveRemoteScreen(
                            remoteUrl = remoteUrl!!,
                            onUnlink = {
                                AntigravityKeepAliveService.stop(this@MainActivity)
                                prefs.edit().remove("google_remote_url").apply()
                                remoteUrl = null
                            }
                        )
                    } else {
                        PairingScreen(
                            onRemoteConfigured = { newUrl ->
                                prefs.edit().putString("google_remote_url", newUrl).apply()
                                remoteUrl = newUrl
                            }
                        )
                    }
                }
            }
        }
    }
}
