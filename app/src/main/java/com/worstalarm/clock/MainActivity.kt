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

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ignored — the alarm degrades gracefully; camera is re-requested at scan time */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeAskForRuntimePermissions()

        setContent {
            WorstAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Navigation(
                        onRequestOverlayPermission = ::openOverlaySettings,
                        onRequestFullScreenIntentPermission = ::openFullScreenIntentSettings
                    )
                }
            }
        }
    }

    // Ask for camera + notifications up front. Camera in particular must be granted BEFORE
    // an alarm rings: the runtime permission dialog can't be shown safely over the ringing
    // lock-screen activity.
    private fun maybeAskForRuntimePermissions() {
        val wanted = buildList {
            add(android.Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ActivityCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) requestPermissions.launch(wanted.toTypedArray())
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

    // Android 14+ can require an explicit grant for USE_FULL_SCREEN_INTENT (below 14 it's
    // auto-granted). Without it, the alarm rings but its full-screen UI never appears over the
    // lock screen — so send the user straight to the per-app setting to turn it on.
    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
