package com.worstalarm.clock.ui.ringing

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.worstalarm.clock.alarm.AlarmService
import com.worstalarm.clock.alarm.AlarmSession
import com.worstalarm.clock.ui.emergency.EmergencyScreen
import com.worstalarm.clock.ui.scanner.BarcodeScanner
import com.worstalarm.clock.ui.theme.WorstAlarmTheme
import kotlinx.coroutines.delay

private enum class Panel { Ringing, Scanning, Emergency }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AlarmRingingRoot(state: AlarmSession.State?) {
    val context = LocalContext.current
    var panel by remember { mutableStateOf(Panel.Ringing) }

    // When the active step changes (scan accepted) or emergency mode toggles, snap back to
    // the Ringing panel so the user sees the next location label + countdown instead of
    // staying on the camera pointed at a barcode that no longer counts.
    LaunchedEffect(state?.currentStepIndex, state?.inEmergencyMode) {
        panel = when {
            state == null -> Panel.Ringing
            state.inEmergencyMode -> Panel.Emergency
            else -> Panel.Ringing
        }
    }

    WorstAlarmTheme {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            if (state == null) {
                // Session ended; AlarmActivity will finish shortly.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Box
            }

            when (panel) {
                Panel.Ringing -> RingingPanel(
                    state = state,
                    onScan = { panel = Panel.Scanning },
                    onEmergency = {
                        context.startService(
                            Intent(context, AlarmService::class.java)
                                .setAction(AlarmService.ACTION_ENTER_EMERGENCY)
                        )
                        panel = Panel.Emergency
                    }
                )
                Panel.Scanning -> {
                    val camera = rememberPermissionState(android.Manifest.permission.CAMERA)
                    LaunchedEffect(Unit) {
                        if (!camera.status.isGranted) camera.launchPermissionRequest()
                    }
                    if (camera.status.isGranted) {
                        ScanningPanel(
                            state = state,
                            onCancel = { panel = Panel.Ringing }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Camera permission required to scan.")
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { camera.launchPermissionRequest() }) {
                                    Text("Grant")
                                }
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(onClick = { panel = Panel.Ringing }) {
                                    Text("Back")
                                }
                            }
                        }
                    }
                }
                Panel.Emergency -> EmergencyScreen(
                    idleResets = state.emergencyIdleResets,
                    maxFreeIdleResets = AlarmSession.MAX_FREE_IDLE_RESETS,
                    alarmStaysOn = state.emergencyIdleResets >= AlarmSession.MAX_FREE_IDLE_RESETS,
                    onCancel = {
                        context.startService(
                            Intent(context, AlarmService::class.java)
                                .setAction(AlarmService.ACTION_EXIT_EMERGENCY)
                        )
                        panel = Panel.Ringing
                    },
                    onComplete = {
                        context.startService(
                            Intent(context, AlarmService::class.java)
                                .setAction(AlarmService.ACTION_EMERGENCY_COMPLETE)
                        )
                    },
                    onIdleTimeout = {
                        // Idle too long — resume the alarm ring, reset the tap counter,
                        // and burn one of the free idle resets.
                        context.startService(
                            Intent(context, AlarmService::class.java)
                                .setAction(AlarmService.ACTION_EMERGENCY_IDLE_RESET)
                        )
                        Toast.makeText(
                            context, "Idle too long — alarm resumed.", Toast.LENGTH_SHORT
                        ).show()
                        panel = Panel.Ringing
                    }
                )
            }
        }
    }
}

@Composable
private fun RingingPanel(
    state: AlarmSession.State,
    onScan: () -> Unit,
    onEmergency: () -> Unit
) {
    Column(
        // systemBarsPadding keeps the big buttons clear of the gesture-navigation bar.
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Step ${state.currentStepIndex + 1} of ${state.totalSteps}",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.alarmWithSteps.alarm.label.ifBlank { "Wake up" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (state.isRingingNow) "SCAN:" else "Next up:",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.currentStep.displayName,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "(barcode: ${state.currentStep.barcode.name})",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            if (!state.isRingingNow && state.nextRingAtMs > 0) {
                Spacer(Modifier.height(16.dp))
                CountdownText(targetMs = state.nextRingAtMs)
            }
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) { Text("SCAN BARCODE", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick = onEmergency,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("EMERGENCY STOP (500 taps)") }
        }
    }
}

@Composable
private fun CountdownText(targetMs: Long) {
    var remainingMs by remember { mutableLongStateOf(targetMs - System.currentTimeMillis()) }
    LaunchedEffect(targetMs) {
        while (remainingMs > 0) {
            remainingMs = targetMs - System.currentTimeMillis()
            delay(250)
        }
    }
    val s = (remainingMs / 1000).coerceAtLeast(0).toInt()
    Text(
        "Next ring in %d:%02d".format(s / 60, s % 60),
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ScanningPanel(
    state: AlarmSession.State,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val expected = state.currentStep.barcode
    val stepIndex = state.currentStepIndex
    var wrongFlash by remember { mutableStateOf(false) }
    // The camera reports the barcode once per frame it's visible in — only the first
    // match may fire, or one physical scan advances multiple steps. The service
    // re-validates as well (see ScanValidator); this just stops the intent spam.
    var scanHandled by remember(stepIndex) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BarcodeScanner { detected ->
                if (detected.rawValue == expected.rawValue &&
                    (expected.format == 0 /*FORMAT_UNKNOWN*/ || detected.format == expected.format)
                ) {
                    if (!scanHandled) {
                        scanHandled = true
                        context.startService(
                            Intent(context, AlarmService::class.java)
                                .setAction(AlarmService.ACTION_SCAN_SUCCESS)
                                .putExtra(AlarmService.EXTRA_STEP_INDEX, stepIndex)
                                .putExtra(AlarmService.EXTRA_SCANNED_VALUE, detected.rawValue)
                                .putExtra(AlarmService.EXTRA_SCANNED_FORMAT, detected.format)
                        )
                    }
                } else {
                    wrongFlash = true
                }
            }
            if (wrongFlash) {
                LaunchedEffect(wrongFlash) { delay(600); wrongFlash = false }
                Box(
                    Modifier.fillMaxSize().background(Color(0x55FF0000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Wrong barcode", color = Color.White, fontSize = 28.sp)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Looking for: ${state.currentStep.displayName}")
            Text(
                "Barcode: ${expected.name}",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Back to ringing screen")
            }
        }
    }
}
