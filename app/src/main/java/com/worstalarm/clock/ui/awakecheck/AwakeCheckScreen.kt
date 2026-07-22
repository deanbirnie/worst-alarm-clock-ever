package com.worstalarm.clock.ui.awakecheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worstalarm.clock.alarm.AwakeCheckPolicy

/**
 * "Are you still awake?" popup. Not a lockdown like the ringing screen — home/back work
 * normally — and while it's up a gentle cue (soft chime + light buzz) repeats so you notice
 * it without watching the screen. The only thing that counts as a genuine response is the
 * button: backing out or ignoring it just leaves the check pending until it times out and the
 * alarm rings again (see AlarmService.handleAwakeCheckTimeout).
 */
@Composable
fun AwakeCheckScreen(checkNumber: Int, onConfirm: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Awake check $checkNumber of ${AwakeCheckPolicy.REQUIRED_DISMISSALS}",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Still awake?",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Confirm you're up. If this isn't dismissed in time, the alarm rings again.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) { Text("I'M AWAKE", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
