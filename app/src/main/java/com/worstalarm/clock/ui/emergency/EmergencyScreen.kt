package com.worstalarm.clock.ui.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val GRID = 4
private const val TARGET_TAPS = EmergencyGamePolicy.TARGET_TAPS

/**
 * Emergency disarm mini-game: a 4x4 grid where exactly one square lights up at a time;
 * the user must tap the lit square 500 times. Any 30-second idle window resets the
 * counter and resumes the alarm. After [maxFreeIdleResets] idle resets the game stops
 * silencing the alarm — it keeps ringing while the user taps ([alarmStaysOn]).
 */
@Composable
fun EmergencyScreen(
    idleResets: Int,
    maxFreeIdleResets: Int,
    alarmStaysOn: Boolean,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onIdleTimeout: () -> Unit
) {
    var taps by remember { mutableIntStateOf(0) }
    var litIndex by remember { mutableIntStateOf(Random.nextInt(GRID * GRID)) }
    var lastTapAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var completed by remember { mutableStateOf(false) }

    // Idle watchdog — stops once the game is won so a post-completion idle can't fire a reset.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (completed) return@LaunchedEffect
            if (EmergencyGamePolicy.isIdleTimedOut(System.currentTimeMillis(), lastTapAtMs)) {
                onIdleTimeout()
                return@LaunchedEffect
            }
        }
    }

    // Fire onComplete exactly once (B6): latch behind `completed`, and the grid stops accepting
    // taps at the target (see the tile's `enabled`), so taps can't advance to 501 and re-trigger.
    LaunchedEffect(taps) {
        if (!completed && EmergencyGamePolicy.isComplete(taps)) {
            completed = true
            onComplete()
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Emergency disarm",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Tap the lit square $TARGET_TAPS times. 30-second idle resets the counter and the alarm will ring again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        if (alarmStaysOn) {
            Text(
                "You've idled out $idleResets times — the alarm keeps ringing until you finish.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (idleResets > 0) {
            Text(
                "Idle resets: $idleResets of $maxFreeIdleResets. After $maxFreeIdleResets, the alarm keeps ringing during the game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Text(
            "$taps / $TARGET_TAPS",
            fontSize = 36.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        LinearProgressIndicator(
            progress = { taps.toFloat() / TARGET_TAPS },
            modifier = Modifier.fillMaxWidth()
        )

        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(GRID) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(GRID) { col ->
                            val index = row * GRID + col
                            val isLit = index == litIndex
                            Box(
                                modifier = Modifier
                                    .size(side / GRID - 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isLit) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable(enabled = isLit && EmergencyGamePolicy.acceptsTap(taps)) {
                                        taps += 1
                                        lastTapAtMs = System.currentTimeMillis()
                                        litIndex = EmergencyGamePolicy.nextLitIndex(litIndex, GRID * GRID)
                                    }
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Back to alarm (resume ringing)") }
    }
}
