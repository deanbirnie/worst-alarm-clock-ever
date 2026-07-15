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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TARGET_TAPS = 500
private const val IDLE_TIMEOUT_MS = 30_000L
private const val GRID = 4

/**
 * Emergency disarm mini-game: a 4x4 grid where exactly one square lights up at a time;
 * the user must tap the lit square 500 times. Any 30-second idle window resets the
 * counter and resumes the alarm.
 */
@Composable
fun EmergencyScreen(
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onIdleTimeout: () -> Unit
) {
    var taps by remember { mutableIntStateOf(0) }
    var litIndex by remember { mutableIntStateOf(Random.nextInt(GRID * GRID)) }
    var lastTapAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Idle watchdog.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (System.currentTimeMillis() - lastTapAtMs >= IDLE_TIMEOUT_MS) {
                onIdleTimeout()
                return@LaunchedEffect
            }
        }
    }

    // If the user hits TARGET_TAPS, disarm.
    DisposableEffect(taps) {
        if (taps >= TARGET_TAPS) onComplete()
        onDispose { }
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
                                    .clickable(enabled = isLit) {
                                        taps += 1
                                        lastTapAtMs = System.currentTimeMillis()
                                        // Pick a new index that isn't the current one.
                                        var next = Random.nextInt(GRID * GRID - 1)
                                        if (next >= litIndex) next += 1
                                        litIndex = next
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
