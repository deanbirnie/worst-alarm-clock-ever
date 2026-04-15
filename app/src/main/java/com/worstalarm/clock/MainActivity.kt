package com.worstalarm.clock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.worstalarm.clock.ui.Navigation
import com.worstalarm.clock.ui.theme.WorstAlarmTheme

class MainActivity : ComponentActivity() {

    private val requestPost = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignored — the app still functions without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeAskForRuntimePermissions()

        setContent {
            WorstAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Navigation(
                        onRequestOverlayPermission = ::openOverlaySettings
                    )
                }
            }
        }
    }

    private fun maybeAskForRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPost.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun openOverlaySettings() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
